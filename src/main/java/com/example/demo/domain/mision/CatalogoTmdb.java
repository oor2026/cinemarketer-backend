package com.example.demo.domain.mision;

import java.util.List;

/**
 * Lo que el catálogo automático necesita saber de TMDB. Es una interfaz propia para que la lógica del catálogo no dependa de
 * Spring ni de la red (se prueba con un catálogo falso); la implementación real es TmdbCatalogoRotacion.
 */
public interface CatalogoTmdb {
    record Genero(long id, String nombre) {}
    record PeliculaPopular(long id, double popularidad) {}
    /**
     * estrenadas = las películas de la colección que ya se estrenaron (es la meta de la misión de esa saga).
     * posterUrl / fondoUrl = la imagen de la colección en TMDB (el póster, para el sello; el fondo ancho, para el detalle). null si no tiene.
     */
    record Saga(long id, String nombre, int estrenadas, String posterUrl, String fondoUrl) {
        public Saga(long id, String nombre, int estrenadas) { this(id, nombre, estrenadas, null, null); }
    }
    /** fotoUrl = la foto de perfil en TMDB (https), o null si no tiene: se usa en el sello del actor, actriz o director. */
    record Persona(long id, String nombre, double popularidad, String fotoUrl) {
        public Persona(long id, String nombre, double popularidad) { this(id, nombre, popularidad, null); }
    }

    /** Una serie popular: su póster (para el sello) y su imagen de fondo (para el detalle), o null si no tiene. */
    record SeriePopular(long id, String nombre, double popularidad, String posterUrl, String fondoUrl) {}

    /** Los géneros de películas. */
    List<Genero> generos();

    /** Las películas más populares en Argentina, de las primeras {@code paginas} páginas. Sin películas para adultos. */
    List<PeliculaPopular> peliculasPopulares(int paginas);

    /** El id de la colección (saga) a la que pertenece la película, o null si no es de ninguna. */
    Long sagaDe(long movieId);

    /** El nombre de una saga y cuántas de sus películas ya se estrenaron. null si no se pudo obtener. */
    Saga saga(long collectionId);

    /** Las personas populares que son actores o actrices, de las primeras {@code paginas} páginas. Sin personas para adultos. */
    List<Persona> actoresPopulares(int paginas);

    /** Los directores de una película. */
    List<Persona> directoresDe(long movieId);

    /** La foto de perfil de una persona (https), o null si no tiene o no se pudo obtener. */
    default String fotoDe(long personaId) { return null; }

    /** En cuántas películas ya estrenadas actuó esa persona. */
    int peliculasComoActor(long personaId);

    /** Cuántas películas ya estrenadas dirigió esa persona. */
    int peliculasComoDirector(long personaId);

    /** Las series más populares de TMDB, de las primeras {@code paginas} páginas, de más a menos populares. Sin repetidas. */
    default List<SeriePopular> seriesPopulares(int paginas) { return List.of(); }

    /** Una serie puntual por su id de TMDB (nombre, popularidad e imágenes). null si no existe o no se pudo obtener. */
    default SeriePopular serie(long id) { return null; }

    /**
     * Los ids de las películas de una colección que ya se estrenaron, tienen póster y no son para adultos (las mismas que muestra el asistente).
     * null si no se pudo consultar: quien llama no debe tocar lo que ya tenía guardado.
     */
    default java.util.Set<Long> peliculasEstrenadasDeColeccion(long collectionId) { return null; }

    /** true si esa película ya se estrenó y tiene póster, false si no, null si no se pudo saber (error de red o respuesta rara). */
    default Boolean peliculaEstrenada(long movieId) { return null; }
}