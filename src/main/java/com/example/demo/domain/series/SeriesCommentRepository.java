package com.example.demo.domain.series;

import com.example.demo.domain.comment.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface SeriesCommentRepository extends JpaRepository<SeriesComment, Long> {

    List<SeriesComment> findBySeriesIdOrderByCreatedAtDesc(Long seriesId);

    long countBySeriesId(Long seriesId);

    @Query("SELECT COUNT(c) FROM SeriesComment c WHERE c.user.id = :userId")
    long countCommentsByUserId(@Param("userId") Long userId);

    void deleteByUser(com.example.demo.domain.user.User user);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT c.user.id) FROM SeriesComment c")
    long countDistinctUsers();

    long countByUserId(Long userId);

    java.util.Optional<SeriesComment> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT c FROM SeriesComment c WHERE c.user.id = :userId " +
            "AND c.moderationStatus NOT IN ('HIDDEN_BY_USER', 'REMOVED', 'REJECTED') " +
            "ORDER BY c.createdAt DESC")
    List<SeriesComment> findLastVisibleByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT c FROM SeriesComment c WHERE c.seriesId = :seriesId " +
            "AND c.moderationStatus NOT IN " +
            "('REMOVED', 'REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER') " +
            "ORDER BY c.createdAt DESC")
    List<SeriesComment> findVisibleBySeriesId(@Param("seriesId") Long seriesId);

    @Query("SELECT c FROM SeriesComment c WHERE c.reportCount > 0 " +
            "AND c.moderationStatus != 'REMOVED' " +
            "ORDER BY c.reportCount DESC")
    List<SeriesComment> findReported();

    @Query("SELECT c FROM SeriesComment c WHERE c.reportCount > 0 " +
            "AND c.adminReviewed = false " +
            "AND c.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY c.createdAt DESC")
    List<SeriesComment> findPending();

    @Query("SELECT c FROM SeriesComment c WHERE c.reportCount > 0 " +
            "AND c.adminReviewed = true " +
            "AND c.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY c.createdAt DESC")
    List<SeriesComment> findInReview();

    List<SeriesComment> findByModerationStatusOrderByCreatedAtDesc(ModerationStatus status);

    @Query("SELECT c FROM SeriesComment c WHERE c.moderationStatus IN ('REMOVED', 'DISMISSED') " +
            "AND c.moderationReviewedAt IS NOT NULL " +
            "ORDER BY c.moderationReviewedAt DESC")
    List<SeriesComment> findResolved();

    @Query("SELECT c FROM SeriesComment c WHERE c.seriesId = :seriesId " +
            "AND c.spoiler = :spoiler " +
            "AND c.moderationStatus NOT IN " +
            "('REMOVED', 'REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER') " +
            "ORDER BY c.createdAt DESC")
    List<SeriesComment> findVisibleBySeriesIdAndSpoiler(@Param("seriesId") Long seriesId,
                                                        @Param("spoiler") boolean spoiler);

    @Query("SELECT c FROM SeriesComment c WHERE c.user.id = :userId " +
            "AND c.seriesId = :seriesId " +
            "AND c.moderationStatus NOT IN ('HIDDEN_BY_USER', 'REMOVED', 'REJECTED') " +
            "ORDER BY c.createdAt DESC")
    List<SeriesComment> findLastVisibleByUserIdAndSeriesId(@Param("userId") Long userId,
                                                           @Param("seriesId") Long seriesId,
                                                           Pageable pageable);

    @Query("SELECT c FROM SeriesComment c WHERE c.user.id = :userId " +
            "AND c.moderationStatus NOT IN ('HIDDEN_BY_USER', 'REMOVED', 'REJECTED') " +
            "ORDER BY c.createdAt DESC")
    List<SeriesComment> findPublicByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT c.seriesId) FROM SeriesComment c WHERE c.user.id = :userId AND c.moderationStatus = 'APPROVED'")
    long countDistinctSeriesCommentedByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM SeriesComment c " +
            "WHERE c.user.id = :userId " +
            "AND c.seriesId = :seriesId " +
            "AND c.moderationStatus = 'HIDDEN_BY_USER'")
    long countHiddenByUserAndSeries(@Param("userId") Long userId,
                                    @Param("seriesId") Long seriesId);

    long countByHasGifTrue();

    @Query("SELECT COUNT(c) FROM SeriesComment c WHERE c.hasGif = true AND c.createdAt BETWEEN :start AND :end")
    long countGifsInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // seriesId guarda el tmdb_id, mismo criterio que Movie/comments.movieId
    @Query("SELECT c.seriesId as id, s.title as title, COUNT(c) as comments " +
            "FROM SeriesComment c JOIN Series s ON c.seriesId = s.tmdbId " +
            "WHERE c.createdAt BETWEEN :start AND :end " +
            "GROUP BY c.seriesId, s.title ORDER BY comments DESC")
    List<Map<String, Object>> findTopSeriesByComments(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      Pageable pageable);

    @Query("SELECT c.user.id as id, u.name as name, COUNT(c) as comments " +
            "FROM SeriesComment c JOIN User u ON c.user.id = u.id " +
            "WHERE c.createdAt BETWEEN :start AND :end " +
            "GROUP BY c.user.id, u.name ORDER BY comments DESC")
    List<Map<String, Object>> findTopUsersByComments(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     Pageable pageable);
}