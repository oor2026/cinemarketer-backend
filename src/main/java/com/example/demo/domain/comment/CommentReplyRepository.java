package com.example.demo.domain.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentReplyRepository extends JpaRepository<CommentReply, Long> {

    // Respuestas visibles de un comentario ordenadas por fecha
    // Incluye REMOVED para mostrar mensaje disciplinatorio en el frontend
    @Query("SELECT r FROM CommentReply r WHERE r.comment.id = :commentId " +
            "AND r.moderationStatus NOT IN ('REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER') " +
            "ORDER BY r.createdAt ASC")
    List<CommentReply> findVisibleByCommentId(@Param("commentId") Long commentId);

    // Contar respuestas visibles
    @Query("SELECT COUNT(r) FROM CommentReply r WHERE r.comment.id = :commentId " +
            "AND r.moderationStatus NOT IN ('REMOVED', 'REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER')")
    long countVisibleByCommentId(@Param("commentId") Long commentId);

    // Batch: mismo conteo pero para varios comentarios de una — reemplaza
    // un countVisibleByCommentId() por cada uno en un loop.
    @Query("SELECT r.comment.id, COUNT(r) FROM CommentReply r WHERE r.comment.id IN :commentIds " +
            "AND r.moderationStatus NOT IN ('REMOVED', 'REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER') " +
            "GROUP BY r.comment.id")
    List<Object[]> countVisibleByCommentIds(@Param("commentIds") List<Long> commentIds);

    // Ultimo comentario del usuario para antispam
    java.util.Optional<CommentReply> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUser(com.example.demo.domain.user.User user);

    List<CommentReply> findByModerationStatusOrderByCreatedAtDesc(ModerationStatus status);

    // Respuestas pendientes: reportadas que el admin aún no revisó
    @Query("SELECT r FROM CommentReply r WHERE r.reportCount > 0 " +
            "AND r.adminReviewed = false " +
            "AND r.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY r.createdAt DESC")
    List<CommentReply> findPending();

    // Respuestas en revisión: reportadas que el admin ya vio pero no resolvió
    @Query("SELECT r FROM CommentReply r WHERE r.reportCount > 0 " +
            "AND r.adminReviewed = true " +
            "AND r.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY r.createdAt DESC")
    List<CommentReply> findInReview();

    // Respuestas resueltas
    @Query("SELECT r FROM CommentReply r WHERE r.moderationStatus IN ('REMOVED', 'DISMISSED') " +
            "AND r.moderationReviewedAt IS NOT NULL " +
            "ORDER BY r.moderationReviewedAt DESC")
    List<CommentReply> findResolved();

    long countByHasGifTrue();
}
