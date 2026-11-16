package com.example.demo.infrastructure.external.tmdb;

import com.example.demo.domain.mision.CatalogoTmdb;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Lo que el catálogo automático le pregunta a TMDB. Usa los métodos «crudos» de TmdbService (que reutilizan sus cachés) y los lee con
 * cuidado: si algo viene con otra forma o sin dato, se ignora en lugar de romper. Las funciones parse* son estáticas para probarlas
 * con respuestas de ejemplo sin conectarse a TMDB.
 */
@Component
public class TmdbCatalogoRotacion implements CatalogoTmdb {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private final TmdbService tmdb;

    public TmdbCatalogoRotacion(TmdbService tmdb) { this.tmdb = tmdb; }

    private static LocalDate hoy() { return LocalDate.now(AR); }

    @Override public List<Genero> generos() { return parseGeneros(tmdb.getMovieGenresRaw()); }

    @Override public List<PeliculaPopular> peliculasPopulares(int paginas) {
        Map<Long, PeliculaPopular> r = new LinkedHashMap<>();
        for (int p = 1; p <= paginas; p++) {
            List<PeliculaPopular> pagina = parsePopulares(tmdb.getPopularMoviesRaw(p));
            if (pagina.isEmpty()) break;
            pagina.forEach(x -> r.putIfAbsent(x.id(), x));
        }
        return new ArrayList<>(r.values());
    }

    @Override public List<SeriePopular> seriesPopulares(int paginas) {
        Map<Long, SeriePopular> r = new LinkedHashMap<>();
        for (int p = 1; p <= paginas; p++) {
            List<SeriePopular> pagina = parseSeriesPopulares(tmdb.getPopularSeriesRaw(p));
            if (pagina.isEmpty()) break;
            pagina.forEach(x -> r.putIfAbsent(x.id(), x));
        }
        return new ArrayList<>(r.values());
    }

    @Override public Long sagaDe(long movieId) { return parseSagaDe(tmdb.getMovieRaw(movieId)); }

    @Override public Saga saga(long collectionId) { return parseSaga(collectionId, tmdb.getCollection(collectionId), hoy()); }

    @Override public List<Persona> actoresPopulares(int paginas) {
        Map<Long, Persona> r = new LinkedHashMap<>();
        for (int p = 1; p <= paginas; p++) {
            List<Persona> pagina = parseActoresPopulares(tmdb.getPopularPeople(p));
            if (pagina.isEmpty()) break;
            pagina.forEach(x -> r.putIfAbsent(x.id(), x));
        }
        return new ArrayList<>(r.values());
    }

    @Override public List<Persona> directoresDe(long movieId) { return parseDirectores(tmdb.getMovieCredits(movieId)); }

    @Override public String fotoDe(long personaId) {
        Object json = tmdb.getPersonRaw(personaId);
        return json instanceof Map<?, ?> m ? imagen(m.get("profile_path"), "w185") : null;
    }

    @Override public int peliculasComoActor(long personaId) { return contarEstrenadas(tmdb.getPersonMovieCredits(personaId), "cast", null, hoy()); }

    @Override public int peliculasComoDirector(long personaId) { return contarEstrenadas(tmdb.getPersonMovieCredits(personaId), "crew", "Director", hoy()); }

    // ------------------------------------------------------------------------------------------------ lectura de las respuestas de TMDB
    static List<Genero> parseGeneros(Object json) {
        List<Genero> r = new ArrayList<>();
        for (Map<?, ?> g : lista(json, "genres")) {
            Long id = largo(g.get("id"));
            if (id != null && g.get("name") instanceof String n && !n.isBlank()) r.add(new Genero(id, n));
        }
        return r;
    }

    static List<PeliculaPopular> parsePopulares(Object json) {
        List<PeliculaPopular> r = new ArrayList<>();
        for (Map<?, ?> m : lista(json, "results")) {
            Long id = largo(m.get("id"));
            if (id == null || Boolean.TRUE.equals(m.get("adult"))) continue;
            r.add(new PeliculaPopular(id, numero(m.get("popularity"))));
        }
        return r;
    }

    /** Las series populares: id, nombre, popularidad y las imágenes (póster para el sello, fondo para el detalle). Sin nombre o sin id, se ignoran. */
    static List<SeriePopular> parseSeriesPopulares(Object json) {
        List<SeriePopular> r = new ArrayList<>();
        for (Map<?, ?> m : lista(json, "results")) {
            Long id = largo(m.get("id"));
            if (id == null || Boolean.TRUE.equals(m.get("adult"))) continue;
            if (m.get("name") instanceof String n && !n.isBlank())
                r.add(new SeriePopular(id, n, numero(m.get("popularity")), imagen(m.get("poster_path"), "w342"), imagen(m.get("backdrop_path"), "w780")));
        }
        return r;
    }

    /** La saga a la que pertenece una película: viene en belongs_to_collection (null si no es de ninguna). */
    static Long parseSagaDe(Object json) {
        if (json instanceof Map<?, ?> m && m.get("belongs_to_collection") instanceof Map<?, ?> c) return largo(c.get("id"));
        return null;
    }

    /** El nombre de la saga y cuántas de sus películas ya se estrenaron (con fecha de estreno pasada y que no sean para adultos). */
    static Saga parseSaga(long id, Object json, LocalDate hoy) {
        if (!(json instanceof Map<?, ?> m) || !(m.get("name") instanceof String nombre)) return null;
        Set<Long> estrenadas = new HashSet<>();
        for (Map<?, ?> p : lista(json, "parts")) {
            Long pid = largo(p.get("id"));
            if (pid != null && !Boolean.TRUE.equals(p.get("adult")) && estrenada(p.get("release_date"), hoy)) estrenadas.add(pid);
        }
        return new Saga(id, nombre, estrenadas.size(), imagen(m.get("poster_path"), "w342"), imagen(m.get("backdrop_path"), "w780"));
    }

    /** La URL de una imagen de TMDB (https). null si no tiene: la aplicación usa entonces el ícono de la categoría. */
    static String imagen(Object path, String tamano) {
        return path instanceof String s && s.startsWith("/") && s.length() > 1 ? "https://image.tmdb.org/t/p/" + tamano + s : null;
    }

    /** Las personas populares que actúan (no las que se dedican a dirigir, producir, etc.) y que no son para adultos. */
    static List<Persona> parseActoresPopulares(Object json) {
        List<Persona> r = new ArrayList<>();
        for (Map<?, ?> m : lista(json, "results")) {
            Long id = largo(m.get("id"));
            if (id == null || Boolean.TRUE.equals(m.get("adult")) || !"Acting".equals(m.get("known_for_department"))) continue;
            if (m.get("name") instanceof String n && !n.isBlank()) r.add(new Persona(id, n, numero(m.get("popularity")), imagen(m.get("profile_path"), "w185")));
        }
        return r;
    }

    static List<Persona> parseDirectores(Object json) {
        List<Persona> r = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        for (Map<?, ?> m : lista(json, "crew")) {
            Long id = largo(m.get("id"));
            if (id == null || !"Director".equals(m.get("job")) || !vistos.add(id)) continue;
            if (m.get("name") instanceof String n && !n.isBlank()) r.add(new Persona(id, n, 0, imagen(m.get("profile_path"), "w185")));
        }
        return r;
    }

    /** Cuántas películas distintas, ya estrenadas, tiene una persona en «cast» o en «crew» (con ese puesto, si se pide). */
    static int contarEstrenadas(Object json, String rol, String puesto, LocalDate hoy) {
        Set<Long> r = new HashSet<>();
        for (Map<?, ?> m : lista(json, rol)) {
            Long id = largo(m.get("id"));
            if (id == null || Boolean.TRUE.equals(m.get("adult")) || !estrenada(m.get("release_date"), hoy)) continue;
            if (puesto != null && !puesto.equals(m.get("job"))) continue;
            r.add(id);
        }
        return r.size();
    }

    // ------------------------------------------------------------------------------------------------ utilidades
    private static boolean estrenada(Object fecha, LocalDate hoy) {
        if (!(fecha instanceof String s) || s.isBlank()) return false;
        try { return !LocalDate.parse(s).isAfter(hoy); } catch (Exception e) { return false; }
    }

    private static List<Map<?, ?>> lista(Object json, String clave) {
        List<Map<?, ?>> r = new ArrayList<>();
        if (json instanceof Map<?, ?> m && m.get(clave) instanceof Iterable<?> it) for (Object o : it) if (o instanceof Map<?, ?> x) r.add(x);
        return r;
    }

    private static Long largo(Object o) { return o instanceof Number n ? n.longValue() : null; }

    private static double numero(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }
}