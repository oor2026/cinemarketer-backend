package com.example.demo.domain.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AdnCinefiloRepository extends JpaRepository<Review, Long> {

    @Query(value = """
        SELECT genre_id, genre_name, SUM(weight) AS total_weight
        FROM (
            -- Solo el género PRINCIPAL de la película (genero_principal_id),
            -- no todos los que trae movie_genre. Join corregido de paso:
            -- r.target_id guarda el tmdb_id (ver ReviewController.voteMovie,
            -- que resuelve por findByTmdbId con el mismo valor), así que el
            -- join correcto es m.tmdb_id = r.target_id — antes usaba
            -- movie_genre.movie_id = r.target_id, que comparaba tmdb_id
            -- contra el PK interno de movies, un cruce incorrecto que venía
            -- de antes de este cambio.
            SELECT g.id AS genre_id, g.name AS genre_name,
                   CASE WHEN r.vote_type = 'LIKE' THEN 1 WHEN r.vote_type = 'DISLIKE' THEN -1 ELSE 0 END AS weight
            FROM reviews r
            JOIN movies m ON m.tmdb_id = r.target_id
            JOIN genres g ON g.id = m.genero_principal_id
            WHERE r.user_id = :userId AND r.review_type = 'MOVIE' AND r.active = true AND r.vote_type IN ('LIKE','DISLIKE')

            -- Comentarios y recomendaciones quedaron fuera del ADN Cinéfilo
            -- a propósito: ninguna de las dos deja saber la intención real
            -- del usuario (un comentario puede ser una crítica negativa, una
            -- recomendación puede estar pensada para el gusto del receptor
            -- y no del que la envía) — sin esa certeza, preferimos un
            -- algoritmo más simple pero fiel a lo que el usuario declaró
            -- explícitamente (el voto LIKE/DISLIKE), antes que uno más rico
            -- pero basado en suposiciones. Si en el futuro se agrega una
            -- forma de que el usuario confirme "esto también refleja mi
            -- gusto" al recomendar, ahí se podría reincorporar con un JOIN
            -- adicional filtrando por ese campo.
        ) sub
        GROUP BY genre_id, genre_name
        """, nativeQuery = true)
    List<Object[]> calcularPesosBase(@Param("userId") Long userId);
}