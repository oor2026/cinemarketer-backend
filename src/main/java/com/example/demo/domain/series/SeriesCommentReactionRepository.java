package com.example.demo.domain.series;

import com.example.demo.domain.comment.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeriesCommentReactionRepository extends JpaRepository<SeriesCommentReaction, Long> {

    @Query("SELECT r FROM SeriesCommentReaction r WHERE r.comment.id = :commentId " +
            "AND r.user.id = :userId AND r.type = :type AND r.reply IS NULL")
    Optional<SeriesCommentReaction> findByCommentIdAndUserIdAndTypeAndNoReply(
            @Param("commentId") Long commentId,
            @Param("userId") Long userId,
            @Param("type") ReactionType type);

    Optional<SeriesCommentReaction> findByCommentIdAndUserIdAndType(
            Long commentId, Long userId, ReactionType type);

    long countByCommentIdAndTypeAndActiveTrue(Long commentId, ReactionType type);

    boolean existsByCommentIdAndUserIdAndTypeAndActiveTrue(
            Long commentId, Long userId, ReactionType type);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE SeriesCommentReaction r SET r.pointLocked = true " +
            "WHERE r.type = 'MERECE_PUNTO' AND r.active = true AND r.pointLocked = false")
    int lockActiveMerecePuntoReactions();

    Optional<SeriesCommentReaction> findByReplyIdAndUserIdAndType(
            Long replyId, Long userId, ReactionType type);

    long countByReplyIdAndTypeAndActiveTrue(Long replyId, ReactionType type);

    boolean existsByReplyIdAndUserIdAndTypeAndActiveTrue(
            Long replyId, Long userId, ReactionType type);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM SeriesCommentReaction r " +
            "WHERE r.comment.id = :commentId AND r.user.id = :userId " +
            "AND r.type = :type AND r.active = true AND r.reply IS NULL")
    boolean existsByCommentIdAndUserIdAndTypeAndActiveTrueAndNoReply(
            @Param("commentId") Long commentId,
            @Param("userId") Long userId,
            @Param("type") ReactionType type);

    @Query("SELECT r FROM SeriesCommentReaction r WHERE r.comment.id = :commentId AND r.type = :type")
    java.util.List<SeriesCommentReaction> findAllByCommentIdAndType(
            @Param("commentId") Long commentId,
            @Param("type") ReactionType type);

    @Query("SELECT COUNT(DISTINCT r.user.id) FROM SeriesCommentReaction r " +
            "JOIN r.comment c WHERE c.user.id = :userId " +
            "AND r.type = 'BANCO' AND r.active = true AND r.reply IS NULL " +
            "AND r.user.id != :userId")
    long countDistinctBancoGiversForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(r) FROM SeriesCommentReaction r " +
            "JOIN r.comment c WHERE c.user.id = :userId " +
            "AND r.type = 'MERECE_PUNTO' AND r.active = true")
    long countMerecePuntosRecibidosByUser(@Param("userId") Long userId);

    long countByCommentIdAndTypeAndActiveTrueAndReplyIsNull(Long commentId, ReactionType type);

    // Batch, mismo criterio que CommentReactionRepository.countByCommentIdsGroupedByType.
    @Query("SELECT r.comment.id, r.type, COUNT(r) FROM SeriesCommentReaction r " +
            "WHERE r.comment.id IN :commentIds AND r.active = true " +
            "GROUP BY r.comment.id, r.type")
    java.util.List<Object[]> countByCommentIdsGroupedByType(@Param("commentIds") java.util.List<Long> commentIds);
}