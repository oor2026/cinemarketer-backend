package com.example.demo.domain.series;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AdnCinefiloSeriesRepository extends JpaRepository<SeriesReview, Long> {

    @Query(value = """
        SELECT genre_name, SUM(weight) AS total_weight
        FROM (
            SELECT g.name AS genre_name,
                   CASE WHEN sr.vote_type = 'LIKE' THEN 1 WHEN sr.vote_type = 'DISLIKE' THEN -1 ELSE 0 END AS weight
            FROM series_reviews sr
            JOIN series s ON s.tmdb_id = sr.series_id
            JOIN series_genre sg ON sg.series_id = s.id
            JOIN genres g ON g.id = sg.genre_id
            WHERE sr.user_id = :userId AND sr.active = true AND sr.vote_type IN ('LIKE','DISLIKE')

            UNION ALL

            SELECT g.name AS genre_name, 2 AS weight
            FROM (SELECT DISTINCT sc.series_id FROM series_comments sc WHERE sc.user_id = :userId) dc
            JOIN series s ON s.tmdb_id = dc.series_id
            JOIN series_genre sg ON sg.series_id = s.id
            JOIN genres g ON g.id = sg.genre_id
        ) sub
        GROUP BY genre_name
        """, nativeQuery = true)
    List<Object[]> calcularPesosBase(@Param("userId") Long userId);
}