package com.example.demo.domain.mision;

/**
 * Algo que hizo el usuario y que puede mover una misión: votar, comentar, acertar una trivia, publicar.
 * Los controladores lo publican con ApplicationEventPublisher DESPUÉS de guardar lo suyo; MisionService lo recibe cuando la
 * transacción ya cerró, así que una falla en las misiones nunca puede romper un voto ni un comentario.
 *
 * @param targetId id de la película o la serie (en TMDB)
 */
public record ActividadEvento(long userId, Tipo tipo, Contenido contenido, Long targetId) {

    public enum Tipo { VOTO, COMENTARIO, TRIVIA, PUBLICACION, INTERVENCION, CANJE, SORTEO, GUSTO, RECOMENDACION }

    public enum Contenido { PELICULA, SERIE }

    public static ActividadEvento votoPelicula(long userId, Long movieId) { return new ActividadEvento(userId, Tipo.VOTO, Contenido.PELICULA, movieId); }
    public static ActividadEvento comentarioPelicula(long userId, Long movieId) { return new ActividadEvento(userId, Tipo.COMENTARIO, Contenido.PELICULA, movieId); }
    public static ActividadEvento votoSerie(long userId, Long seriesId) { return new ActividadEvento(userId, Tipo.VOTO, Contenido.SERIE, seriesId); }
    public static ActividadEvento comentarioSerie(long userId, Long seriesId) { return new ActividadEvento(userId, Tipo.COMENTARIO, Contenido.SERIE, seriesId); }

    // Trivia y publicaciones no tienen un título concreto (targetId = null). Contenido solo importa si la misión pide un tipo.
    public static ActividadEvento triviaPelicula(long userId) { return new ActividadEvento(userId, Tipo.TRIVIA, Contenido.PELICULA, null); }
    public static ActividadEvento triviaSerie(long userId) { return new ActividadEvento(userId, Tipo.TRIVIA, Contenido.SERIE, null); }
    public static ActividadEvento publicacion(long userId) { return new ActividadEvento(userId, Tipo.PUBLICACION, Contenido.PELICULA, null); }

    // Estos no son sobre una película: su targetId identifica otra cosa (una publicación, un premio, un espacio de gustos). Contenido solo importa en gustos y recomendaciones.
    /** Comentario o respuesta del usuario en una publicación de Comunidad. targetId = id de la publicación. */
    public static ActividadEvento intervencion(long userId, Long publicacionId) { return new ActividadEvento(userId, Tipo.INTERVENCION, Contenido.PELICULA, publicacionId); }
    /** Canje de un premio. targetId = id del premio (los premios Premium van en NEGATIVO, para no mezclarse con los comunes). */
    public static ActividadEvento canje(long userId, Long premioId) { return new ActividadEvento(userId, Tipo.CANJE, Contenido.PELICULA, premioId); }
    /** Participación en un sorteo. targetId = id del premio sorteado. */
    public static ActividadEvento sorteo(long userId, Long premioId) { return new ActividadEvento(userId, Tipo.SORTEO, Contenido.PELICULA, premioId); }
    /** Gusto guardado en Mis gustos. espacio: 1 a 4 = películas (favorita, última vista en cine, no me canso, no la banco); 5 a 8 = series (favorita, última maratón, no me canso, no la banco). */
    public static ActividadEvento gusto(long userId, int espacio, boolean serie) { return new ActividadEvento(userId, Tipo.GUSTO, serie ? Contenido.SERIE : Contenido.PELICULA, (long) espacio); }
    public static ActividadEvento recomendacionPelicula(long userId, Long movieId) { return new ActividadEvento(userId, Tipo.RECOMENDACION, Contenido.PELICULA, movieId); }
    public static ActividadEvento recomendacionSerie(long userId, Long seriesId) { return new ActividadEvento(userId, Tipo.RECOMENDACION, Contenido.SERIE, seriesId); }
}
