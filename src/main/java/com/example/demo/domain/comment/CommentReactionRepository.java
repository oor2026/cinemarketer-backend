package com.example.demo.domain.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    // Buscar reaccion especifica de un usuario sobre un comentario
    Optional<CommentReaction> findByCommentIdAndUserIdAndType(
            Long commentId, Long userId, ReactionType type);

    // Contar reacciones activas de un tipo para un comentario
    long countByCommentIdAndTypeAndActiveTrue(Long commentId, ReactionType type);

    // Verificar si existe una reaccion activa
    boolean existsByCommentIdAndUserIdAndTypeAndActiveTrue(
            Long commentId, Long userId, ReactionType type);

    // Bloquear todas las reacciones MERECE_PUNTO activas (job mensual)
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE CommentReaction r SET r.pointLocked = true " +
           "WHERE r.type = 'MERECE_PUNTO' AND r.active = true AND r.pointLocked = false")
    int lockActiveMerecePuntoReactions();

    Optional<CommentReaction> findByReplyIdAndUserIdAndType(
            Long replyId, Long userId, ReactionType type);

    long countByReplyIdAndTypeAndActiveTrue(Long replyId, ReactionType type);

    boolean existsByReplyIdAndUserIdAndTypeAndActiveTrue(
            Long replyId, Long userId, ReactionType type);
}
