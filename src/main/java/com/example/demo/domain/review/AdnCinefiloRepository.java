package com.example.demo.domain.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AdnCinefiloRepository extends JpaRepository<Review, Long> {

    @Query(value = """
        SELECT genre_name, SUM(weight) AS total_weight
        FROM (
            SELECT g.name AS genre_name,
                   CASE WHEN r.vote_type = 'LIKE' THEN 1 WHEN r.vote_type = 'DISLIKE' THEN -1 ELSE 0 END AS weight
            FROM reviews r
            JOIN movie_genre mg ON mg.movie_id = r.target_id
            JOIN genres g ON g.id = mg.genre_id
            WHERE r.user_id = :userId AND r.review_type = 'MOVIE' AND r.active = true AND r.vote_type IN ('LIKE','DISLIKE')

            UNION ALL

            SELECT g.name AS genre_name, 2 AS weight
            FROM (SELECT DISTINCT c.movie_id FROM comments c WHERE c.user_id = :userId) dc
            JOIN movies m ON m.tmdb_id = dc.movie_id
            JOIN movie_genre mg ON mg.movie_id = m.id
            JOIN genres g ON g.id = mg.genre_id

            UNION ALL

            SELECT g.name AS genre_name, 3 AS weight
            FROM (SELECT DISTINCT mr.movie_id FROM movie_recommendations mr WHERE mr.sender_id = :userId) dr
            JOIN movies m ON m.tmdb_id = dr.movie_id
            JOIN movie_genre mg ON mg.movie_id = m.id
            JOIN genres g ON g.id = mg.genre_id
        ) sub
        GROUP BY genre_name
        """, nativeQuery = true)
    List<Object[]> calcularPesosBase(@Param("userId") Long userId);
}