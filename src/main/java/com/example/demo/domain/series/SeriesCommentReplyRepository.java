package com.example.demo.domain.series;

import com.example.demo.domain.comment.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeriesCommentReplyRepository extends JpaRepository<SeriesCommentReply, Long> {

    @Query("SELECT r FROM SeriesCommentReply r WHERE r.comment.id = :commentId " +
            "AND r.moderationStatus NOT IN ('REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER') " +
            "ORDER BY r.createdAt ASC")
    List<SeriesCommentReply> findVisibleByCommentId(@Param("commentId") Long commentId);

    @Query("SELECT COUNT(r) FROM SeriesCommentReply r WHERE r.comment.id = :commentId " +
            "AND r.moderationStatus NOT IN ('REMOVED', 'REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER')")
    long countVisibleByCommentId(@Param("commentId") Long commentId);

    java.util.Optional<SeriesCommentReply> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUser(com.example.demo.domain.user.User user);

    List<SeriesCommentReply> findByModerationStatusOrderByCreatedAtDesc(ModerationStatus status);

    @Query("SELECT r FROM SeriesCommentReply r WHERE r.reportCount > 0 " +
            "AND r.adminReviewed = false " +
            "AND r.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY r.createdAt DESC")
    List<SeriesCommentReply> findPending();

    @Query("SELECT r FROM SeriesCommentReply r WHERE r.reportCount > 0 " +
            "AND r.adminReviewed = true " +
            "AND r.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY r.createdAt DESC")
    List<SeriesCommentReply> findInReview();

    @Query("SELECT r FROM SeriesCommentReply r WHERE r.moderationStatus IN ('REMOVED', 'DISMISSED') " +
            "AND r.moderationReviewedAt IS NOT NULL " +
            "ORDER BY r.moderationReviewedAt DESC")
    List<SeriesCommentReply> findResolved();

    long countByHasGifTrue();
}