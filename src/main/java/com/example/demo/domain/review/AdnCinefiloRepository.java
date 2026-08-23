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

            UNION ALL

            -- Comentarios quedó fuera del ADN Cinéfilo: es señal de
            -- engagement (se detuvo a escribir algo), no de sentimiento
            -- hacia el género — sin análisis de contenido no sabemos si
            -- "odio esta película" debe pesar igual que "la amé".

            SELECT g.id AS genre_id, g.name AS genre_name, 3 AS weight
            FROM (SELECT DISTINCT mr.movie_id FROM movie_recommendations mr WHERE mr.sender_id = :userId) dr
            JOIN movies m ON m.tmdb_id = dr.movie_id
            JOIN genres g ON g.id = m.genero_principal_id
        ) sub
        GROUP BY genre_id, genre_name
        """, nativeQuery = true)
    List<Object[]> calcularPesosBase(@Param("userId") Long userId);
}