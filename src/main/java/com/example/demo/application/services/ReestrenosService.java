package com.example.demo.application.services;

import com.example.demo.application.dtos.MovieFilterDto;
import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.application.dtos.external.tmdb.TmdbPageResponseDto;
import com.example.demo.application.dtos.external.tmdb.TmdbReleaseDatesDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * "Reestrenos": películas que ya existen y vuelven a cines en Argentina, ya sea que estén en cartelera desde hace
 * poco o que estén anunciadas para los próximos días. Alimenta el carrusel "Reestrenos" del feed de películas.
 *
 * Cómo se arma (TMDb no tiene una marca de "reestreno", ver ReestrenoDetector):
 *   1. Se le pide a TMDb las películas con una fecha de estreno en cines en Argentina dentro de la ventana
 *      (de 30 días atrás a 90 días adelante). Incluye estrenos nuevos y reestrenos, y TMDb devuelve la fecha ORIGINAL
 *      de la película: las que son de hace más de un año son las candidatas.
 *   2. A cada candidata se le piden sus fechas de estreno por país y el detector decide si es un reestreno.
 *   3. Se ordenan por fecha de reestreno: primero las que ya están en cines, después las anunciadas.
 *
 * Mismo esquema que ProximosEstrenosService: el resultado vive en memoria, se renueva cada TTL_MINUTOS y, cuando vence,
 * se sigue sirviendo el viejo mientras se renueva en segundo plano.
 *
 * Si TMDb falla, no se pierde nada de lo que ya estaba detectado: si falla en una película puntual, esa conserva su
 * reestreno anterior; si falla en todo, se sigue sirviendo el pool viejo. Y se reintenta en REINTENTO_MINUTOS (no en
 * TTL_MINUTOS, ni en cada pedido, para no golpear a TMDb mientras está con problemas).
 */
@Service
public class ReestrenosService {

    private static final Logger log = LoggerFactory.getLogger(ReestrenosService.class);

    private static final int PAGINAS_A_TRAER = 5;     // hasta ~100 películas con estreno en cines en Argentina en la ventana
    private static final int MAX_CANDIDATAS = 40;     // tope de películas viejas a las que se les piden las fechas por país
    private static final long TTL_MINUTOS = 180;
    private static final long REINTENTO_MINUTOS = 5;   // si hubo fallas, se vuelve a intentar antes
    private static final ZoneId ZONA_AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private final MovieService movieService;

    private volatile List<TmdbMovieDto> pool = null;
    private volatile LocalDateTime expira = null;

    public ReestrenosService(MovieService movieService) {
        this.movieService = movieService;
    }

    /** Calienta el pool al arrancar, en un hilo aparte para no demorar el arranque. */
    @PostConstruct
    public void calentar() {
        new Thread(this::refrescar, "reestrenos-warmup").start();
    }

    public TmdbPageResponseDto obtener() {
        if (pool == null) {
            refrescar();
        } else if (expira != null && LocalDateTime.now().isAfter(expira)) {
            new Thread(this::refrescar, "reestrenos-refresh").start();
        }

        List<TmdbMovieDto> actual = pool != null ? pool : List.of();

        // La ventana corre con los días: lo que ya hace más de 30 días que empezó se descarta al leer, y lo recién
        // anunciado no entra hasta que se renueva el pool.
        LocalDate hoy = LocalDate.now(ZONA_AR);
        String desde = hoy.minusDays(ReestrenoDetector.DIAS_ATRAS).toString();
        List<TmdbMovieDto> vigentes = new ArrayList<>();
        for (TmdbMovieDto m : actual) {
            if (m.getReestreno() != null && m.getReestreno().getFecha() != null
                    && m.getReestreno().getFecha().compareTo(desde) >= 0) {
                vigentes.add(m);
            }
        }

        TmdbPageResponseDto respuesta = new TmdbPageResponseDto();
        respuesta.setPage(1);
        respuesta.setResults(vigentes);
        respuesta.setTotalPages(1);
        respuesta.setTotalResults(vigentes.size());
        return respuesta;
    }

    private synchronized void refrescar() {
        // Otro hilo pudo haberlo renovado mientras este esperaba el lock.
        if (pool != null && expira != null && LocalDateTime.now().isBefore(expira)) return;

        try {
            LocalDate hoy = LocalDate.now(ZONA_AR);

            // 1) Candidatas: con estreno en cines en Argentina en la ventana, pero originalmente de hace más de un año.
            //    SIN sortBy a propósito: con sortBy, MovieService pide por separado en español e inglés y dejaría afuera
            //    las películas en otros idiomas (coreano, japonés...). TMDb ya ordena por popularidad por defecto.
            Map<Long, TmdbMovieDto> candidatas = new LinkedHashMap<>();
            int totalPaginas = 1;
            for (int p = 1; p <= PAGINAS_A_TRAER && p <= totalPaginas; p++) {
                MovieFilterDto filter = new MovieFilterDto();
                filter.setRegion(ReestrenoDetector.PAIS);
                filter.setWithReleaseType("2|3");
                filter.setRegionDateGte(hoy.minusDays(ReestrenoDetector.DIAS_ATRAS).toString());
                filter.setRegionDateLte(hoy.plusDays(ReestrenoDetector.DIAS_ADELANTE).toString());
                filter.setPage(p);

                TmdbPageResponseDto res = movieService.searchMovies(filter);
                if (res == null || res.getResults() == null || res.getResults().isEmpty()) break;
                if (res.getTotalPages() != null) totalPaginas = res.getTotalPages();

                for (TmdbMovieDto m : res.getResults()) {
                    if (m.getId() != null && esDeHaceMasDeUnAnio(m.getReleaseDate(), hoy)) candidatas.putIfAbsent(m.getId(), m);
                }
            }

            // 2) El detector decide cuáles son reestrenos de verdad.
            List<TmdbMovieDto> poolAnterior = pool;
            Map<Long, TmdbMovieDto> anteriores = poolAnterior == null ? Map.of()
                    : poolAnterior.stream().filter(m -> m.getId() != null).collect(Collectors.toMap(TmdbMovieDto::getId, m -> m, (a, b) -> a));
            List<TmdbMovieDto> reestrenos = new ArrayList<>();
            int analizadas = 0, fallidas = 0;
            for (TmdbMovieDto m : candidatas.values()) {
                if (analizadas++ >= MAX_CANDIDATAS) break;
                try {
                    TmdbReleaseDatesDto fechas = movieService.getFechasDeEstreno(m.getId());
                    Optional<ReestrenoDetector.Reestreno> r = ReestrenoDetector.detectar(fechas, m.getReleaseDate(), hoy);
                    if (r.isPresent()) {
                        TmdbMovieDto.Reestreno dto = new TmdbMovieDto.Reestreno();
                        dto.setFecha(r.get().fecha().toString());
                        dto.setNota(r.get().nota());
                        m.setReestreno(dto);
                        reestrenos.add(m);
                    }
                } catch (Exception e) {
                    fallidas++;
                    // Una falla pasajera no tiene que hacer desaparecer un reestreno que ya estaba detectado.
                    TmdbMovieDto previo = anteriores.get(m.getId());
                    if (previo != null) reestrenos.add(previo);
                    log.warn("No se pudieron leer las fechas de estreno de la película {}: {}", m.getId(), e.getMessage());
                }
            }

            // Si TMDb falló en TODAS las consultas, no se pisa un pool bueno con uno vacío.
            if (fallidas > 0 && fallidas == Math.min(analizadas, MAX_CANDIDATAS)) {
                throw new IllegalStateException("TMDb falló en las " + fallidas + " consultas de fechas de estreno");
            }

            // 3) Primero las que ya están en cines, después las anunciadas; a igual fecha, la más popular.
            reestrenos.sort((a, b) -> {
                int cmp = a.getReestreno().getFecha().compareTo(b.getReestreno().getFecha());
                if (cmp != 0) return cmp;
                double pa = a.getPopularity() != null ? a.getPopularity() : 0.0;
                double pb = b.getPopularity() != null ? b.getPopularity() : 0.0;
                return Double.compare(pb, pa);
            });

            pool = reestrenos;
            // Si hubo fallas, el resultado está completo a medias: se vuelve a intentar pronto en vez de esperar las 3 horas.
            expira = LocalDateTime.now().plusMinutes(fallidas > 0 ? REINTENTO_MINUTOS : TTL_MINUTOS);
            log.info("Reestrenos renovados: {} reestrenos de {} candidatas ({} con error)", reestrenos.size(), candidatas.size(), fallidas);
        } catch (Exception e) {
            log.warn("No se pudieron renovar los reestrenos: {}", e.getMessage());
            if (pool == null) {
                // Sin nada que servir: pool vacío y reintento en 1 minuto, para no golpear a TMDb en cada pedido si está caído.
                pool = List.of();
                expira = LocalDateTime.now().plusMinutes(1);
            } else {
                // Se sigue sirviendo lo que había. Sin esto, cada pedido dispararía un nuevo intento contra TMDb mientras esté caído.
                expira = LocalDateTime.now().plusMinutes(REINTENTO_MINUTOS);
            }
        }
    }

    /** La fecha original (ISO, "AAAA-MM-DD") es de hace al menos un año. Sin fecha, no es candidata. */
    private static boolean esDeHaceMasDeUnAnio(String fechaOriginal, LocalDate hoy) {
        if (fechaOriginal == null || fechaOriginal.isBlank()) return false;
        return fechaOriginal.compareTo(hoy.minusDays(ReestrenoDetector.MARGEN_DIAS).toString()) <= 0;
    }
}