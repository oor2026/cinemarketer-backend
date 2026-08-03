package com.example.demo.domain.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    // Buscar reaccion especifica de un usuario sobre un comentario
    // Para comentarios (reply_id IS NULL)
    @Query("SELECT r FROM CommentReaction r WHERE r.comment.id = :commentId " +
            "AND r.user.id = :userId AND r.type = :type AND r.reply IS NULL")
    Optional<CommentReaction> findByCommentIdAndUserIdAndTypeAndNoReply(
            @Param("commentId") Long commentId,
            @Param("userId") Long userId,
            @Param("type") ReactionType type);

    // Para cualquier busqueda (usado en merece punto que no tiene reply)
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

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM CommentReaction r " +
            "WHERE r.comment.id = :commentId AND r.user.id = :userId " +
            "AND r.type = :type AND r.active = true AND r.reply IS NULL")
    boolean existsByCommentIdAndUserIdAndTypeAndActiveTrueAndNoReply(
            @Param("commentId") Long commentId,
            @Param("userId") Long userId,
            @Param("type") ReactionType type);

    // Obtener todas las reacciones MERECE_PUNTO de un comentario (para revertir puntos al eliminar)
    @Query("SELECT r FROM CommentReaction r WHERE r.comment.id = :commentId AND r.type = :type")
    java.util.List<CommentReaction> findAllByCommentIdAndType(
            @Param("commentId") Long commentId,
            @Param("type") ReactionType type);

    // "Te banco" recibidos de usuarios DIFERENTES (para insignias)
    @Query("SELECT COUNT(DISTINCT r.user.id) FROM CommentReaction r " +
            "JOIN r.comment c WHERE c.user.id = :userId " +
            "AND r.type = 'BANCO' AND r.active = true AND r.reply IS NULL " +
            "AND r.user.id != :userId")
    long countDistinctBancoGiversForUser(@Param("userId") Long userId);

    // "Merecés un punto" recibidos (para insignias)
    @Query("SELECT COUNT(r) FROM CommentReaction r " +
            "JOIN r.comment c WHERE c.user.id = :userId " +
            "AND r.type = 'MERECE_PUNTO' AND r.active = true")
    long countMerecePuntosRecibidosByUser(@Param("userId") Long userId);

    // Contar reacciones activas de un tipo para un comentario, EXCLUYENDO las de sus respuestas
    long countByCommentIdAndTypeAndActiveTrueAndReplyIsNull(Long commentId, ReactionType type);
}