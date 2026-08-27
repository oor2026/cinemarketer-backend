package com.example.demo.domain.series;

import com.example.demo.domain.review.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SeriesReviewRepository extends JpaRepository<SeriesReview, Long> {

    long countBySeriesIdAndVote(Long seriesId, VoteType vote);

    Optional<SeriesReview> findByUserIdAndSeriesId(Long userId, Long seriesId);

    boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

    List<SeriesReview> findByUserIdAndVoteIsNotNullOrderByCreatedAtDesc(Long userId);

    // Misma búsqueda pero paginada en la base — reemplaza traer todo
    // el historial de votos de series y cortar a 6 con .stream().limit()
    // en Java.
    org.springframework.data.domain.Page<SeriesReview> findByUserIdAndVoteIsNotNullOrderByCreatedAtDesc(
            Long userId, org.springframework.data.domain.Pageable pageable);

    long countByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(sr) FROM SeriesReview sr WHERE sr.vote = :voteType AND sr.createdAt BETWEEN :start AND :end")
    long countByVoteTypeInPeriod(@org.springframework.data.repository.query.Param("voteType") com.example.demo.domain.review.VoteType voteType,
                                 @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
                                 @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);

    long countByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);

    // Top series más votadas — sr.seriesId guarda el tmdb_id (mismo criterio que Movie)
    @org.springframework.data.jpa.repository.Query("SELECT sr.seriesId as id, COALESCE(s.title, 'Serie ' || sr.seriesId) as title, " +
            "COUNT(sr) as votes, " +
            "SUM(CASE WHEN sr.vote = 'LIKE' THEN 1 ELSE 0 END) as likes, " +
            "SUM(CASE WHEN sr.vote = 'DISLIKE' THEN 1 ELSE 0 END) as dislikes " +
            "FROM SeriesReview sr LEFT JOIN Series s ON sr.seriesId = s.tmdbId " +
            "WHERE sr.createdAt BETWEEN :start AND :end " +
            "GROUP BY sr.seriesId, s.title ORDER BY votes DESC")
    List<Map<String, Object>> findTopSeriesByVotes(@org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
                                                   @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
                                                   org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT u.id as id, u.name as name, COUNT(sr) as votes " +
            "FROM SeriesReview sr JOIN User u ON sr.user.id = u.id " +
            "WHERE sr.createdAt BETWEEN :start AND :end " +
            "GROUP BY u.id, u.name ORDER BY votes DESC")
    List<Map<String, Object>> findTopUsersByVotes(@org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
                                                  @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
                                                  org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT FUNCTION('DATE', sr.createdAt) as date, COUNT(sr) as count " +
            "FROM SeriesReview sr WHERE sr.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', sr.createdAt) ORDER BY date")
    List<Object[]> getDailyVoteCount(@org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
                                     @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);
}