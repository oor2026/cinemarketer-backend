package com.example.demo.domain.publication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicationCommentRepository extends JpaRepository<PublicationComment, Long> {

    Page<PublicationComment> findByPublicationIdAndHiddenFalseOrderByCreatedAtAsc(
            Long publicationId, Pageable pageable);

    long countByPublicationIdAndHiddenFalseAndParentCommentIdIsNull(Long publicationId);

    // Comentarios raíz (sin padre)
    Page<PublicationComment> findByPublicationIdAndHiddenFalseAndParentCommentIdIsNullOrderByCreatedAtAsc(
            Long publicationId, Pageable pageable);

    // Respuestas de un comentario — todas (usado para replyCount)
    List<PublicationComment> findByParentCommentIdAndHiddenFalseOrderByCreatedAtAsc(Long parentCommentId);

    // Respuestas paginadas
    Page<PublicationComment> findByParentCommentIdAndHiddenFalseOrderByCreatedAtAsc(Long parentCommentId, Pageable pageable);

    // ── Moderación (mismo patrón que CommentRepository, independiente) ────────

    // Pendientes: reportados que el admin aún no revisó
    @org.springframework.data.jpa.repository.Query("SELECT c FROM PublicationComment c WHERE c.reportCount > 0 " +
            "AND c.adminReviewed = false " +
            "AND c.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY c.createdAt DESC")
    List<PublicationComment> findPending();

    // En revisión: reportados que el admin ya vio pero no resolvió
    @org.springframework.data.jpa.repository.Query("SELECT c FROM PublicationComment c WHERE c.reportCount > 0 " +
            "AND c.adminReviewed = true " +
            "AND c.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY c.createdAt DESC")
    List<PublicationComment> findInReview();

    // Resueltos: eliminados o desestimados
    @org.springframework.data.jpa.repository.Query("SELECT c FROM PublicationComment c WHERE c.moderationStatus IN ('REMOVED', 'DISMISSED') " +
            "AND c.moderationReviewedAt IS NOT NULL " +
            "ORDER BY c.moderationReviewedAt DESC")
    List<PublicationComment> findResolved();
}