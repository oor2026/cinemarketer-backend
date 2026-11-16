package com.example.demo.infrastructure.external.tmdb;

import com.example.demo.domain.mision.CatalogoTmdb.*;

import java.time.LocalDate;
import java.util.*;

/** Prueba cómo se leen las respuestas de TMDB: con la forma real, con datos incompletos y con basura. Sin conectarse a TMDB. */
public class TmdbCatalogoRotacionTest {
    static int ok = 0, fal = 0;
    static void chk(boolean c, String m) { if (c) { ok++; System.out.println("  ok: " + m); } else { fal++; System.out.println("  FALLA: " + m); } }
    static Map<String, Object> m(Object... kv) { Map<String, Object> r = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]); return r; }
    static final LocalDate HOY = LocalDate.of(2026, 11, 12);

    public static void main(String[] args) {
        System.out.println("=== géneros");
        List<Genero> g = TmdbCatalogoRotacion.parseGeneros(m("genres", List.of(m("id", 28, "name", "Acción"), m("id", 10770, "name", "Película de TV"), m("id", "x", "name", "Mal"), m("id", 5, "name", " "), m("name", "Sin id"))));
        chk(g.size() == 2 && g.get(0).id() == 28 && g.get(0).nombre().equals("Acción"), "se leen los géneros válidos (id numérico y nombre) y se ignoran los incompletos: " + g.size());

        System.out.println("=== películas populares");
        List<PeliculaPopular> p = TmdbCatalogoRotacion.parsePopulares(m("results", List.of(m("id", 10, "popularity", 55.5, "adult", false), m("id", 11, "popularity", 40, "adult", true), m("popularity", 9), m("id", 12))));
        chk(p.size() == 2 && p.get(0).popularidad() == 55.5 && p.get(1).id() == 12 && p.get(1).popularidad() == 0, "se descartan las adultas y las sin id; la popularidad que falta es 0");

        System.out.println("=== series populares");
        List<SeriePopular> sp = TmdbCatalogoRotacion.parseSeriesPopulares(m("results", List.of(
                m("id", 3000, "name", "ALF", "popularity", 88.5, "poster_path", "/alf.jpg", "backdrop_path", "/alf-fondo.jpg"),
                m("id", 3001, "name", "Los Simpson", "popularity", 70),
                m("id", 3002, "name", " "), m("name", "Sin id", "popularity", 5), m("id", 3003, "name", "Para adultos", "adult", true), m("id", 3004, "popularity", 1))));
        chk(sp.size() == 2 && sp.get(0).id() == 3000 && sp.get(0).nombre().equals("ALF") && sp.get(0).popularidad() == 88.5, "se leen las series válidas (id, nombre, popularidad); sin nombre, sin id o para adultos se ignoran: " + sp.size());
        chk("https://image.tmdb.org/t/p/w342/alf.jpg".equals(sp.get(0).posterUrl()) && "https://image.tmdb.org/t/p/w780/alf-fondo.jpg".equals(sp.get(0).fondoUrl()), "el póster (w342) y el fondo (w780) salen como URL de TMDB");
        chk(sp.get(1).posterUrl() == null && sp.get(1).fondoUrl() == null && TmdbCatalogoRotacion.parseSeriesPopulares("basura").isEmpty() && TmdbCatalogoRotacion.parseSeriesPopulares(null).isEmpty(), "una serie sin imágenes las deja en null y una respuesta rara no rompe");

        System.out.println("=== saga de una película");
        chk(TmdbCatalogoRotacion.parseSagaDe(m("id", 671, "belongs_to_collection", m("id", 1241, "name", "Harry Potter - Colección"))) == 1241L, "se lee la colección de belongs_to_collection");
        chk(TmdbCatalogoRotacion.parseSagaDe(m("id", 1, "belongs_to_collection", null)) == null && TmdbCatalogoRotacion.parseSagaDe(m("id", 1)) == null && TmdbCatalogoRotacion.parseSagaDe("basura") == null && TmdbCatalogoRotacion.parseSagaDe(null) == null, "si no es de ninguna saga, falta el campo o la respuesta es basura: null");

        System.out.println("=== una saga: cuántas ya se estrenaron");
        Saga s = TmdbCatalogoRotacion.parseSaga(1241, m("id", 1241, "name", "Harry Potter - Colección", "parts", List.of(
                m("id", 1, "release_date", "2001-11-16"), m("id", 2, "release_date", "2002-11-15"), m("id", 3, "release_date", "2026-11-12"), m("id", 4, "release_date", "2027-01-01"),
                m("id", 5, "release_date", ""), m("id", 6), m("id", 7, "release_date", "no-es-fecha"), m("id", 8, "release_date", "2010-01-01", "adult", true), m("id", 2, "release_date", "2002-11-15"))), HOY);
        chk(s != null && s.nombre().equals("Harry Potter - Colección") && s.estrenadas() == 3, "cuenta solo las estrenadas (incluida la de HOY); no las futuras, sin fecha, de fecha inválida, adultas ni repetidas: " + (s == null ? null : s.estrenadas()));
        Saga con = TmdbCatalogoRotacion.parseSaga(1241, m("id", 1241, "name", "Harry Potter - Colección", "poster_path", "/hp.jpg", "backdrop_path", "/fondo.jpg", "parts", List.of()), HOY);
        chk("https://image.tmdb.org/t/p/w342/hp.jpg".equals(con.posterUrl()) && "https://image.tmdb.org/t/p/w780/fondo.jpg".equals(con.fondoUrl()), "la saga trae su póster (para el sello) y su fondo (para el detalle) como URL https");
        Saga sin = TmdbCatalogoRotacion.parseSaga(1, m("id", 1, "name", "X", "poster_path", null, "backdrop_path", "sin-barra", "parts", List.of()), HOY);
        chk(sin.posterUrl() == null && sin.fondoUrl() == null, "sin imagen (o con una ruta rara) queda en null: se usa el ícono de la categoría");
        chk(TmdbCatalogoRotacion.parseSaga(1, m("id", 1, "parts", List.of()), HOY) == null && TmdbCatalogoRotacion.parseSaga(1, "basura", HOY) == null && TmdbCatalogoRotacion.parseSaga(1, null, HOY) == null, "sin nombre o con basura: null (no se arma la saga)");
        chk(TmdbCatalogoRotacion.parseSaga(1, m("name", "Sin partes"), HOY).estrenadas() == 0, "una saga sin lista de películas tiene 0 estrenadas");

        System.out.println("=== actores y actrices populares");
        System.out.println("=== foto de perfil de una persona");
        List<Persona> pf = TmdbCatalogoRotacion.parseActoresPopulares(m("results", List.of(m("id", 1, "name", "Con foto", "known_for_department", "Acting", "popularity", 1.0, "profile_path", "/cara.jpg"), m("id", 2, "name", "Sin foto", "known_for_department", "Acting", "popularity", 1.0, "profile_path", null))));
        chk("https://image.tmdb.org/t/p/w185/cara.jpg".equals(pf.get(0).fotoUrl()) && pf.get(1).fotoUrl() == null, "el actor trae su foto (w185, https) y, si no tiene, null");
        List<Persona> df = TmdbCatalogoRotacion.parseDirectores(m("crew", List.of(m("id", 525, "name", "Christopher Nolan", "job", "Director", "profile_path", "/nolan.jpg"))));
        chk("https://image.tmdb.org/t/p/w185/nolan.jpg".equals(df.get(0).fotoUrl()), "el director trae su foto");
        List<Persona> a = TmdbCatalogoRotacion.parseActoresPopulares(m("results", List.of(m("id", 1, "name", "Meryl Streep", "known_for_department", "Acting", "popularity", 80.1), m("id", 2, "name", "Un director", "known_for_department", "Directing"),
                m("id", 3, "name", "Adulto", "known_for_department", "Acting", "adult", true), m("id", 4, "name", " ", "known_for_department", "Acting"), m("id", 5, "known_for_department", "Acting"))));
        chk(a.size() == 1 && a.get(0).id() == 1 && a.get(0).popularidad() == 80.1, "solo quedan las personas que actúan, sin adultas y con nombre: " + a.size());

        System.out.println("=== directores de una película");
        List<Persona> d = TmdbCatalogoRotacion.parseDirectores(m("cast", List.of(m("id", 9, "name", "Un actor")), "crew", List.of(m("id", 525, "name", "Christopher Nolan", "job", "Director"), m("id", 525, "name", "Christopher Nolan", "job", "Director"),
                m("id", 7, "name", "Un productor", "job", "Producer"), m("id", 8, "job", "Director"))));
        chk(d.size() == 1 && d.get(0).id() == 525 && d.get(0).nombre().equals("Christopher Nolan"), "solo el puesto Director, una vez, con nombre");

        System.out.println("=== cuántas películas estrenadas tiene una persona");
        Object cred = m("cast", List.of(m("id", 1, "release_date", "2010-01-01"), m("id", 2, "release_date", "2030-01-01"), m("id", 3, "release_date", ""), m("id", 4, "release_date", "2000-05-05", "adult", true), m("id", 1, "release_date", "2010-01-01"), m("id", 5, "release_date", "1999-09-09")),
                "crew", List.of(m("id", 10, "job", "Director", "release_date", "2015-01-01"), m("id", 11, "job", "Producer", "release_date", "2015-01-01"), m("id", 12, "job", "Director", "release_date", "2099-01-01"), m("id", 10, "job", "Director", "release_date", "2015-01-01")));
        chk(TmdbCatalogoRotacion.contarEstrenadas(cred, "cast", null, HOY) == 2, "como actor: 2 (la futura, la sin fecha, la adulta y la repetida no cuentan)");
        chk(TmdbCatalogoRotacion.contarEstrenadas(cred, "crew", "Director", HOY) == 1, "como director: 1 (no cuenta producir, ni la no estrenada, ni la repetida)");
        chk(TmdbCatalogoRotacion.contarEstrenadas(null, "cast", null, HOY) == 0 && TmdbCatalogoRotacion.contarEstrenadas("basura", "cast", null, HOY) == 0 && TmdbCatalogoRotacion.contarEstrenadas(m(), "crew", "Director", HOY) == 0, "con basura o sin datos: 0");

        System.out.println("=== respuestas vacías o basura");
        chk(TmdbCatalogoRotacion.parseGeneros(null).isEmpty() && TmdbCatalogoRotacion.parsePopulares("x").isEmpty() && TmdbCatalogoRotacion.parseActoresPopulares(m("results", "no-es-lista")).isEmpty() && TmdbCatalogoRotacion.parseDirectores(m()).isEmpty(), "ninguna lectura se rompe con null, texto o campos con otra forma: devuelven vacío");

        System.out.println("\n" + ok + " verificaciones correctas, " + fal + " fallas");
        if (fal > 0) System.exit(1);
    }
}
