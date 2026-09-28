package com.example.demo.application.services;

import com.example.demo.application.dtos.MovieFilterDto;
import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.application.dtos.external.tmdb.TmdbPageResponseDto;
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

/**
 * "Lo que se viene": las estrenos futuros más populares, en orden
 * cronológico (la más próxima a estrenar primero).
 *
 * TMDb solo permite UN criterio de orden por pedido: si se ordena por
 * fecha, el catálogo indie/de nicho tapa a los títulos conocidos (hacen
 * falta decenas de páginas para encontrarlos); si se ordena por
 * popularidad, se pierde el orden cronológico. Solución: se piden las
 * más populares (rápido, trae lo conocido), se juntan varias páginas en
 * un pool, se ordena el pool por fecha y se sirve por páginas desde la
 * memoria. Así el orden es correcto de punta a punta, no solo dentro de
 * cada tanda.
 *
 * El pool vive en memoria (unos cientos de objetos chicos, sin nada en
 * la base) y se renueva cada TTL_MINUTOS. Cuando vence, se sigue
 * sirviendo el pool viejo mientras se renueva en segundo plano, así
 * ningún usuario espera por el refresco.
 */
@Service
public class ProximosEstrenosService {

    private static final Logger log = LoggerFactory.getLogger(ProximosEstrenosService.class);

    private static final int PAGINAS_A_TRAER = 8;   // universo de candidatas (~300)
    // De las candidatas solo se conservan las más populares antes de ordenar
    // por fecha: sin este corte, los estrenos más próximos (que suelen ser
    // films chicos) encabezaban la lista.
    private static final double FRACCION_MAS_POPULARES = 0.25;
    private static final int MIN_TITULOS = 40;
    private static final int MAX_TITULOS = 100;
    private static final int TAMANIO_PAGINA = 20;
    private static final long TTL_MINUTOS = 30;
    private static final ZoneId ZONA_AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private final MovieService movieService;

    private volatile List<TmdbMovieDto> pool = null;
    private volatile LocalDateTime expira = null;

    public ProximosEstrenosService(MovieService movieService) {
        this.movieService = movieService;
    }

    /** Calienta el pool al arrancar, en un hilo aparte para no demorar el arranque. */
    @PostConstruct
    public void calentar() {
        new Thread(this::refrescar, "proximos-estrenos-warmup").start();
    }

    public TmdbPageResponseDto obtenerPagina(Integer page) {
        int pagina = (page != null && page > 0) ? page : 1;

        if (pool == null) {
            // Primera vez y el warmup todavía no terminó: se espera acá
            // (refrescar es synchronized, así que si el warmup ya está
            // corriendo, este hilo espera a que termine y sale rápido).
            refrescar();
        } else if (expira != null && LocalDateTime.now().isAfter(expira)) {
            // Vencido: se sirve el viejo y se renueva en segundo plano.
            new Thread(this::refrescar, "proximos-estrenos-refresh").start();
        }

        List<TmdbMovieDto> actual = pool != null ? pool : List.of();

        // El pool puede tener títulos que ya estrenaron desde el último
        // refresco (dura hasta 30 min) — se descartan al leer.
        String hoy = LocalDate.now(ZONA_AR).toString();
        List<TmdbMovieDto> vigentes = new ArrayList<>();
        for (TmdbMovieDto m : actual) {
            String fecha = m.getReleaseDate();
            if (fecha != null && !fecha.isBlank() && fecha.compareTo(hoy) > 0) vigentes.add(m);
        }

        int total = vigentes.size();
        int totalPaginas = Math.max(1, (int) Math.ceil(total / (double) TAMANIO_PAGINA));
        int desde = Math.min((pagina - 1) * TAMANIO_PAGINA, total);
        int hasta = Math.min(desde + TAMANIO_PAGINA, total);

        TmdbPageResponseDto respuesta = new TmdbPageResponseDto();
        respuesta.setPage(pagina);
        respuesta.setResults(new ArrayList<>(vigentes.subList(desde, hasta)));
        respuesta.setTotalPages(totalPaginas);
        respuesta.setTotalResults(total);
        return respuesta;
    }

    /**
     * synchronized a propósito: evita que 2 hilos armen el pool a la vez.
     * MovieService no llama de vuelta a esta clase, así que no hay riesgo
     * de que se bloqueen entre sí.
     */
    private synchronized void refrescar() {
        // Otro hilo pudo haberlo renovado mientras este esperaba el lock.
        if (pool != null && expira != null && LocalDateTime.now().isBefore(expira)) return;

        try {
            String hoy = LocalDate.now(ZONA_AR).toString();
            Map<Long, TmdbMovieDto> porId = new LinkedHashMap<>();

            for (int p = 1; p <= PAGINAS_A_TRAER; p++) {
                MovieFilterDto filter = new MovieFilterDto();
                filter.setSortBy("popularity.desc");
                filter.setReleaseDateGteExact(hoy);
                filter.setPage(p);

                TmdbPageResponseDto res = movieService.searchMovies(filter);
                if (res == null || res.getResults() == null || res.getResults().isEmpty()) break;

                for (TmdbMovieDto m : res.getResults()) {
                    if (m.getId() != null) porId.putIfAbsent(m.getId(), m);
                }
            }

            // Primero el corte por popularidad...
            List<TmdbMovieDto> candidatas = new ArrayList<>(porId.values());
            candidatas.sort((a, b) -> {
                double pa = a.getPopularity() != null ? a.getPopularity() : 0.0;
                double pb = b.getPopularity() != null ? b.getPopularity() : 0.0;
                return Double.compare(pb, pa);
            });
            int conservar = (int) Math.round(candidatas.size() * FRACCION_MAS_POPULARES);
            conservar = Math.max(MIN_TITULOS, Math.min(MAX_TITULOS, conservar));
            conservar = Math.min(conservar, candidatas.size());

            // ...y recién ahí, el orden cronológico.
            List<TmdbMovieDto> ordenada = new ArrayList<>(candidatas.subList(0, conservar));
            ordenada.sort((a, b) -> {
                // Fechas ISO (YYYY-MM-DD): se comparan bien como texto.
                String fa = (a.getReleaseDate() != null && !a.getReleaseDate().isBlank()) ? a.getReleaseDate() : "9999-12-31";
                String fb = (b.getReleaseDate() != null && !b.getReleaseDate().isBlank()) ? b.getReleaseDate() : "9999-12-31";
                int cmp = fa.compareTo(fb);
                if (cmp != 0) return cmp;
                double pa = a.getPopularity() != null ? a.getPopularity() : 0.0;
                double pb = b.getPopularity() != null ? b.getPopularity() : 0.0;
                return Double.compare(pb, pa); // mismo día: la más popular primero
            });

            pool = ordenada;
            expira = LocalDateTime.now().plusMinutes(TTL_MINUTOS);
            log.info("Pool de 'Lo que se viene' renovado: {} títulos", ordenada.size());
        } catch (Exception e) {
            log.warn("No se pudo renovar el pool de 'Lo que se viene': {}", e.getMessage());
            if (pool == null) {
                // Sin nada que servir: pool vacío y reintento en 1 minuto,
                // para no golpear a TMDb en cada pedido si está caído.
                pool = List.of();
                expira = LocalDateTime.now().plusMinutes(1);
            }
        }
    }
}
