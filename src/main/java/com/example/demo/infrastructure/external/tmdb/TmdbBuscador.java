package com.example.demo.infrastructure.external.tmdb;

import com.example.demo.domain.mision.BuscadorTmdb;
import com.example.demo.domain.mision.RotacionTextos;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Los buscadores del panel de admin sobre TMDB. Usa los métodos «crudos» de TmdbService (que reutilizan sus cachés) y lee las respuestas con
 * cuidado: lo que viene incompleto o con otra forma se ignora en lugar de romper. Las funciones parse* son estáticas para probarlas con
 * respuestas de ejemplo sin conectarse a TMDB.
 */
@Component
public class TmdbBuscador implements BuscadorTmdb {

    static final long ID_GENERO_PELICULA_DE_TV = 10770;
    static final int MAX_RESULTADOS = 15, MIN_LETRAS = 2;
    private static final String IMAGENES = "https://image.tmdb.org/t/p/w92";

    private final TmdbService tmdb;

    public TmdbBuscador(TmdbService tmdb) { this.tmdb = tmdb; }

    @Override public List<Opcion> generos() { return parseGeneros(tmdb.getMovieGenresRaw()); }

    @Override public Opcion genero(long id) { return generos().stream().filter(g -> g.id() == id).findFirst().orElse(null); }

    @Override public List<Opcion> personas(String texto) { return corto(texto) ? List.of() : parsePersonas(tmdb.searchPeopleRaw(texto.trim())); }

    @Override public List<Opcion> sagas(String texto) { return corto(texto) ? List.of() : parseSagas(tmdb.searchCollectionsRaw(texto.trim())); }

    @Override public Opcion persona(long id) { return parsePersona(tmdb.getPersonRaw(id)); }

    @Override public Opcion saga(long id) { return parseSaga(tmdb.getCollection(id)); }

    private static boolean corto(String t) { return t == null || t.trim().length() < MIN_LETRAS; }

    // ------------------------------------------------------------------------------------------------ lectura de las respuestas de TMDB
    static List<Opcion> parseGeneros(Object json) {
        List<Opcion> r = new ArrayList<>();
        for (Map<?, ?> g : lista(json, "genres")) {
            Long id = largo(g.get("id"));
            if (id == null || id == ID_GENERO_PELICULA_DE_TV || !(g.get("name") instanceof String n) || n.isBlank()) continue;
            r.add(new Opcion(id, n.trim(), null, null));
        }
        return r;
    }

    static List<Opcion> parsePersonas(Object json) {
        List<Opcion> r = new ArrayList<>();
        for (Map<?, ?> p : lista(json, "results")) {
            Opcion o = persona(p);
            if (o != null) r.add(o);
            if (r.size() >= MAX_RESULTADOS) break;
        }
        return r;
    }

    /** El detalle de una sola persona (/person/{id}): trae el nombre, la foto y el área, pero no «known_for». */
    static Opcion parsePersona(Object json) { return json instanceof Map<?, ?> m ? persona(m) : null; }

    private static Opcion persona(Map<?, ?> p) {
        Long id = largo(p.get("id"));
        if (id == null || Boolean.TRUE.equals(p.get("adult")) || !(p.get("name") instanceof String n) || n.isBlank()) return null;
        StringBuilder detalle = new StringBuilder(area(p.get("known_for_department")));
        List<String> conocidas = new ArrayList<>();
        for (Map<?, ?> k : lista(p, "known_for")) {
            Object t = k.get("title") != null ? k.get("title") : k.get("name");
            if (t instanceof String s && !s.isBlank() && conocidas.size() < 2) conocidas.add(s.trim());
        }
        if (!conocidas.isEmpty()) detalle.append(detalle.length() > 0 ? " · " : "").append(String.join(", ", conocidas));
        return new Opcion(id, n.trim(), imagen(p.get("profile_path")), detalle.length() == 0 ? null : detalle.toString());
    }

    static List<Opcion> parseSagas(Object json) {
        List<Opcion> r = new ArrayList<>();
        for (Map<?, ?> c : lista(json, "results")) {
            Opcion o = saga(c);
            if (o != null) r.add(o);
            if (r.size() >= MAX_RESULTADOS) break;
        }
        return r;
    }

    static Opcion parseSaga(Object json) { return json instanceof Map<?, ?> m ? saga(m) : null; }

    /** El nombre de la saga sale limpio («Harry Potter», no «Harry Potter - Colección»), igual que en las misiones del catálogo automático. */
    private static Opcion saga(Map<?, ?> c) {
        Long id = largo(c.get("id"));
        if (id == null || Boolean.TRUE.equals(c.get("adult")) || !(c.get("name") instanceof String n) || n.isBlank()) return null;
        return new Opcion(id, RotacionTextos.nombreSaga(n), imagen(c.get("poster_path")), null);
    }

    // ------------------------------------------------------------------------------------------------ utilidades
    static String area(Object departamento) {
        if (!(departamento instanceof String d) || d.isBlank()) return "";
        return switch (d) {
            case "Acting" -> "Actuación"; case "Directing" -> "Dirección"; case "Writing" -> "Guion"; case "Production" -> "Producción";
            case "Camera" -> "Cámara"; case "Editing" -> "Edición"; case "Sound" -> "Sonido"; case "Art" -> "Arte"; default -> d;
        };
    }

    private static String imagen(Object path) { return path instanceof String s && s.startsWith("/") && s.length() > 1 ? IMAGENES + s : null; }

    private static List<Map<?, ?>> lista(Object json, String clave) {
        List<Map<?, ?>> r = new ArrayList<>();
        if (json instanceof Map<?, ?> m && m.get(clave) instanceof Iterable<?> it) for (Object o : it) if (o instanceof Map<?, ?> x) r.add(x);
        return r;
    }

    private static Long largo(Object o) { return o instanceof Number n ? n.longValue() : null; }
}
