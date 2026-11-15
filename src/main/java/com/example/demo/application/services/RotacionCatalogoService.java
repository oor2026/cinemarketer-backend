package com.example.demo.application.services;

import com.example.demo.domain.mision.CatalogoTmdb;
import com.example.demo.domain.mision.CatalogoTmdb.*;
import com.example.demo.domain.mision.RotacionDatos;
import com.example.demo.domain.mision.RotacionDatos.Objetivo;
import com.example.demo.domain.mision.RotacionDatos.Resultado;
import com.example.demo.domain.mision.RotacionTextos;
import com.example.demo.domain.mision.SagasFijas;
import com.example.demo.domain.mision.SagasFijas.SagaFija;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;

/**
 * El catálogo AUTOMÁTICO de misiones rotativas: arma, desde lo popular de TMDB en Argentina, la lista de sagas, actores y actrices,
 * directores y géneros que después se sortea entre los usuarios (ver MisionService). Corre una vez por mes (RotacionCatalogoScheduler).
 *
 * Criterios (todos ajustables acá):
 *   · GÉNERO: todos los de TMDB menos «Película de TV». Meta 10.
 *   · SAGA: SOLO las sagas armadas de Cinemarketer (SagasFijas, las mismas del asistente del feed), no las que salgan en TMDB. Tiene que
 *     tener al menos 2 películas ya estrenadas. La meta es la cantidad de películas ESTRENADAS de la colección (sin tope).
 *   · ACTOR/ACTRIZ: de las personas populares que actúan, las que tienen al menos 8 películas estrenadas (para que la meta de 5 sea
 *     alcanzable). Sin personas para adultos. Meta 5.
 *   · DIRECTOR: los que más aparecen entre las películas populares, con al menos 8 películas dirigidas. Meta 5.
 *
 * El catálogo SOLO CRECE: no desactiva ni borra nada (eso rompería la misión de quien la tiene en juego). Lo que el admin excluyó
 * o desactivó no se vuelve a tocar. Cada familia se arma por separado: si una falla, las otras igual se completan.
 */
@Service
public class RotacionCatalogoService {

    private static final Logger log = LoggerFactory.getLogger(RotacionCatalogoService.class);

    static final int PAGINAS_PELICULAS = 6;          // ~120 películas populares
    static final int PAGINAS_PERSONAS = 5;           // ~100 personas populares
    static final int MAX_ACTORES = 60, MAX_DIRECTORES = 40;
    static final int MIN_SAGA_ESTRENADAS = 2, MIN_PELICULAS_PERSONA = 8;
    static final long ID_GENERO_PELICULA_DE_TV = 10770;
    static final long PAUSA_MS = 80;                 // un respiro entre pedido y pedido a TMDB

    /** Lo que pasó en una corrida. porFamilia = qué se creó / actualizó / omitió; errores = lo que no se pudo hacer. */
    public record Resumen(Map<String, Resultado> porFamilia, List<String> errores) {
        public String texto() {
            StringBuilder s = new StringBuilder();
            porFamilia.forEach((f, r) -> s.append(f).append(": +").append(r.creados()).append(" nuevos, ").append(r.actualizados()).append(" actualizados, ")
                    .append(r.sinCambios()).append(" sin cambios, ").append(r.omitidos()).append(" omitidos. "));
            if (!errores.isEmpty()) s.append("PROBLEMAS: ").append(String.join(" | ", errores));
            return s.toString().trim();
        }
    }

    private final CatalogoTmdb tmdb;
    private final RotacionDatos datos;
    private final long pausaMs;
    private final List<SagaFija> sagasFijas;

    @Autowired
    public RotacionCatalogoService(CatalogoTmdb tmdb, RotacionDatos datos) { this(tmdb, datos, PAUSA_MS, SagasFijas.TODAS); }

    /** Para las pruebas: sin pausas y con unas pocas sagas fijas de ejemplo (ids 100, 200, 300 y 400). */
    RotacionCatalogoService(CatalogoTmdb tmdb, RotacionDatos datos, long pausaMs) {
        this(tmdb, datos, pausaMs, List.of(new SagaFija(100, "Harry Potter"), new SagaFija(200, "El Señor de los Anillos"), new SagaFija(300, "Una sola película"), new SagaFija(400, "Rápidos y Furiosos")));
    }

    RotacionCatalogoService(CatalogoTmdb tmdb, RotacionDatos datos, long pausaMs, List<SagaFija> sagasFijas) {
        this.tmdb = tmdb;
        this.datos = datos;
        this.pausaMs = pausaMs;
        this.sagasFijas = List.copyOf(sagasFijas);
    }

    public int cantidadObjetivos() { return datos.cantidadObjetivos(); }

    public Resumen refrescar() {
        Map<String, Resultado> porFamilia = new LinkedHashMap<>();
        List<String> errores = new ArrayList<>();
        List<PeliculaPopular> populares = List.of();
        try {
            populares = tmdb.peliculasPopulares(PAGINAS_PELICULAS);
        } catch (Exception e) {
            errores.add("películas populares: " + e.getMessage());
            log.warn("[rotación] no se pudieron obtener las películas populares: {}", e.getMessage());
        }
        final List<PeliculaPopular> pop = populares;
        familia("GENERO", porFamilia, errores, this::generos);
        familia("SAGA", porFamilia, errores, this::sagas);
        familia("ACTOR", porFamilia, errores, this::actores);
        familia("DIRECTOR", porFamilia, errores, () -> directores(pop));
        completarFotos("ACTOR");
        completarFotos("DIRECTOR");
        return new Resumen(porFamilia, errores);
    }

    /**
     * Solo las sagas. Es barato (una consulta a TMDB por cada saga fija) y se corre en cada arranque del servidor: así, cuando se suma una
     * saga nueva al buscador (SagasFijas) y se despliega, su misión aparece sola, sin esperar al día 1 del mes ni tocar nada en el admin.
     */
    public Resumen refrescarSagas() {
        Map<String, Resultado> porFamilia = new LinkedHashMap<>();
        List<String> errores = new ArrayList<>();
        familia("SAGA", porFamilia, errores, this::sagas);
        return new Resumen(porFamilia, errores);
    }

    private void familia(String nombre, Map<String, Resultado> porFamilia, List<String> errores, Supplier<List<Objetivo>> armar) {
        try {
            Optional<Resultado> r = datos.aplicar(armar.get());
            if (r.isPresent()) porFamilia.put(nombre, r.get());
            else errores.add(nombre + ": otro proceso lo está corriendo");
        } catch (Exception e) {
            errores.add(nombre + ": " + e.getMessage());
            log.warn("[rotación] no se pudo armar la familia {}: {}", nombre, e.getMessage());
        }
    }

    /**
     * Los actores y directores que ya estaban en el catálogo y no figuran entre los populares de este mes (o quedaron sin foto) la piden
     * directamente a TMDB. Si una persona no tiene foto, o falla el pedido, se saltea: su sello queda con el ícono de su categoría.
     */
    private void completarFotos(String familia) {
        try {
            for (Long id : datos.personasSinFoto(familia)) {
                try {
                    String url = tmdb.fotoDe(id);
                    pausa();
                    if (url != null) datos.ponerFoto(familia, id, url);
                } catch (Exception e) { log.warn("[rotación] no se pudo obtener la foto de {} {}: {}", familia, id, e.getMessage()); }
            }
        } catch (Exception e) { log.warn("[rotación] no se pudieron completar las fotos de {}: {}", familia, e.getMessage()); }
    }

    private void pausa() {
        if (pausaMs <= 0) return;
        try { Thread.sleep(pausaMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // ------------------------------------------------------------------------------------------------ géneros
    private List<Objetivo> generos() {
        List<Objetivo> r = new ArrayList<>();
        for (Genero g : tmdb.generos()) {
            if (g.id() == ID_GENERO_PELICULA_DE_TV || vacio(g.nombre())) continue;
            r.add(new Objetivo("GENERO", g.id(), RotacionTextos.nombre(g.nombre()), RotacionTextos.META_GENERO, 0));
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------------ sagas
    private List<Objetivo> sagas() {
        List<Objetivo> r = new ArrayList<>();
        for (SagaFija fija : sagasFijas) {
            Saga s;
            try { s = tmdb.saga(fija.id()); } catch (Exception ex) { log.warn("[rotación] no se pudo obtener la saga {}: {}", fija.id(), ex.getMessage()); continue; }
            pausa();
            if (s == null || s.estrenadas() < MIN_SAGA_ESTRENADAS) continue;
            r.add(new Objetivo("SAGA", fija.id(), RotacionTextos.nombre(fija.nombre()), s.estrenadas(), 0, s.posterUrl(), s.fondoUrl()));       // la meta es la cantidad de estrenadas, sin tope
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------------ actores y actrices
    private List<Objetivo> actores() {
        List<Objetivo> r = new ArrayList<>();
        for (Persona p : tmdb.actoresPopulares(PAGINAS_PERSONAS)) {
            if (r.size() >= MAX_ACTORES) break;
            if (vacio(p.nombre())) continue;
            int peliculas;
            try { peliculas = tmdb.peliculasComoActor(p.id()); } catch (Exception e) { continue; }
            pausa();
            if (peliculas >= MIN_PELICULAS_PERSONA) r.add(new Objetivo("ACTOR", p.id(), RotacionTextos.nombre(p.nombre()), RotacionTextos.META_ACTOR, p.popularidad(), p.fotoUrl(), null));
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------------ directores
    private List<Objetivo> directores(List<PeliculaPopular> populares) {
        Map<Long, String> nombres = new HashMap<>(), fotos = new HashMap<>();
        Map<Long, Integer> veces = new LinkedHashMap<>();
        for (PeliculaPopular p : populares) {
            List<Persona> ds;
            try { ds = tmdb.directoresDe(p.id()); } catch (Exception e) { continue; }
            pausa();
            for (Persona d : ds) {
                if (vacio(d.nombre())) continue;
                nombres.putIfAbsent(d.id(), d.nombre());
                if (d.fotoUrl() != null) fotos.putIfAbsent(d.id(), d.fotoUrl());
                veces.merge(d.id(), 1, Integer::sum);
            }
        }
        List<Map.Entry<Long, Integer>> orden = new ArrayList<>(veces.entrySet());
        orden.sort((a, b) -> !a.getValue().equals(b.getValue()) ? Integer.compare(b.getValue(), a.getValue()) : nombres.get(a.getKey()).compareTo(nombres.get(b.getKey())));

        List<Objetivo> r = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : orden) {
            if (r.size() >= MAX_DIRECTORES) break;
            int peliculas;
            try { peliculas = tmdb.peliculasComoDirector(e.getKey()); } catch (Exception ex) { continue; }
            pausa();
            if (peliculas >= MIN_PELICULAS_PERSONA) r.add(new Objetivo("DIRECTOR", e.getKey(), RotacionTextos.nombre(nombres.get(e.getKey())), RotacionTextos.META_DIRECTOR, e.getValue(), fotos.get(e.getKey()), null));
        }
        return r;
    }

    private static boolean vacio(String s) { return s == null || s.isBlank(); }
}