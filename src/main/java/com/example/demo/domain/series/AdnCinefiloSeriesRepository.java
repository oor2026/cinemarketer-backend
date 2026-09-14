package com.example.demo.domain.series;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AdnCinefiloSeriesRepository extends JpaRepository<SeriesReview, Long> {

    @Query(value = """
        SELECT genre_id, genre_name, SUM(weight) AS total_weight
        FROM (
            -- Solo el género PRINCIPAL de la serie (genero_principal_id),
            -- no todos los que trae series_genre. El join contra tmdb_id
            -- ya estaba bien acá desde antes, a diferencia de Películas.
            SELECT g.id AS genre_id, g.name AS genre_name,
                   CASE WHEN sr.vote_type = 'LIKE' THEN 1 WHEN sr.vote_type = 'DISLIKE' THEN -1 ELSE 0 END AS weight
            FROM series_reviews sr
            JOIN series s ON s.tmdb_id = sr.series_id
            JOIN genres g ON g.id = s.genero_principal_id
            WHERE sr.user_id = :userId AND sr.active = true AND sr.vote_type IN ('LIKE','DISLIKE')

            -- Comentarios y recomendaciones quedaron fuera del ADN Cinéfilo
            -- (mismo criterio que en Películas): ninguna de las dos permite
            -- saber la intención real del usuario hacia el género — se
            -- prefiere un algoritmo más simple pero fiel a lo declarado
            -- explícitamente (el voto), antes que uno que asuma intención.
        ) sub
        GROUP BY genre_id, genre_name
        """, nativeQuery = true)
    List<Object[]> calcularPesosBase(@Param("userId") Long userId);
}