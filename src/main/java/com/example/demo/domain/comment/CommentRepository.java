package com.example.demo.domain.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // ── Existentes ────────────────────────────────────────────────────────────

    List<Comment> findByMovieIdOrderByCreatedAtDesc(Long movieId);

    long countByMovieId(Long movieId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.user.id = :userId")
    long countCommentsByUserId(@Param("userId") Long userId);

    void deleteByUser(com.example.demo.domain.user.User user);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT c.movieId as id, m.title as title, COUNT(c) as comments " +
            "FROM Comment c JOIN Movie m ON c.movieId = m.tmdbId " +  // movieId guarda el tmdb_id, no el id interno
            "WHERE c.createdAt BETWEEN :start AND :end " +
            "GROUP BY c.movieId, m.title ORDER BY comments DESC")
    List<Map<String, Object>> findTopMoviesByComments(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      Pageable pageable);

    @Query("SELECT c.user.id as id, u.name as name, COUNT(c) as comments " +
            "FROM Comment c JOIN User u ON c.user.id = u.id " +
            "WHERE c.createdAt BETWEEN :start AND :end " +
            "GROUP BY c.user.id, u.name ORDER BY comments DESC")
    List<Map<String, Object>> findTopUsersByComments(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     Pageable pageable);

    @Query("SELECT COUNT(DISTINCT c.user.id) FROM Comment c")
    long countDistinctUsers();

    long countByUserId(Long userId);

    // ── Antispam ──────────────────────────────────────────────────────────────

    // Último comentario publicado por el usuario (para validar duplicados)
    java.util.Optional<Comment> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    // Último comentario visible del usuario (excluye ocultos — para antispam)
    @Query("SELECT c FROM Comment c WHERE c.user.id = :userId " +
            "AND c.moderationStatus NOT IN ('HIDDEN_BY_USER', 'REMOVED', 'REJECTED') " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findLastVisibleByUserId(@Param("userId") Long userId, Pageable pageable);

    // ── Moderación ────────────────────────────────────────────────────────────

    // Comentarios visibles en el frontend (excluye ocultos, eliminados y rechazados)
    @Query("SELECT c FROM Comment c WHERE c.movieId = :movieId " +
            "AND c.moderationStatus NOT IN " +
            "('REMOVED', 'REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER') " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findVisibleByMovieId(@Param("movieId") Long movieId);

    // Comentarios reportados (para admin — pestaña 1)
    @Query("SELECT c FROM Comment c WHERE c.reportCount > 0 " +
            "AND c.moderationStatus != 'REMOVED' " +
            "ORDER BY c.reportCount DESC")
    List<Comment> findReported();

    // Pendientes: reportados que el admin aún no revisó
    @Query("SELECT c FROM Comment c WHERE c.reportCount > 0 " +
            "AND c.adminReviewed = false " +
            "AND c.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findPending();

    // En revisión: reportados que el admin ya vio pero no resolvió
    @Query("SELECT c FROM Comment c WHERE c.reportCount > 0 " +
            "AND c.adminReviewed = true " +
            "AND c.moderationStatus NOT IN ('REMOVED', 'DISMISSED') " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findInReview();

    // Comentarios pendientes de revisión (para admin — pestaña 2)
    List<Comment> findByModerationStatusOrderByCreatedAtDesc(ModerationStatus status);

    // Comentarios resueltos (eliminados o desestimados)
    @Query("SELECT c FROM Comment c WHERE c.moderationStatus IN ('REMOVED', 'DISMISSED') " +
            "AND c.moderationReviewedAt IS NOT NULL " +
            "ORDER BY c.moderationReviewedAt DESC")
    List<Comment> findResolved();

    @Query("SELECT c FROM Comment c WHERE c.movieId = :movieId " +
            "AND c.spoiler = :spoiler " +
            "AND c.moderationStatus NOT IN " +
            "('REMOVED', 'REJECTED', 'AUTO_HIDDEN', 'HIDDEN_BY_USER') " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findVisibleByMovieIdAndSpoiler(@Param("movieId") Long movieId,
                                                 @Param("spoiler") boolean spoiler);

    @Query("SELECT c FROM Comment c WHERE c.user.id = :userId " +
            "AND c.movieId = :movieId " +
            "AND c.moderationStatus NOT IN ('HIDDEN_BY_USER', 'REMOVED', 'REJECTED') " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findLastVisibleByUserIdAndMovieId(@Param("userId") Long userId,
                                                    @Param("movieId") Long movieId,
                                                    Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.user.id = :userId " +
            "AND c.moderationStatus NOT IN ('HIDDEN_BY_USER', 'REMOVED', 'REJECTED') " +
            "ORDER BY c.createdAt DESC")
    List<Comment> findPublicByUserId(@Param("userId") Long userId, Pageable pageable);

    // Mismo filtro que findPublicByUserId — countByUserId (sin filtro)
    // se usaba mal para la paginación de "Mi actividad", contando
    // ocultos/reportados que findPublicByUserId ya no devolvía. Esto
    // dejaba el mazo pidiendo páginas de más, con cards vacías al final.
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.user.id = :userId " +
            "AND c.moderationStatus NOT IN ('HIDDEN_BY_USER', 'REMOVED', 'REJECTED')")
    long countPublicByUserId(@Param("userId") Long userId);

    // Comentarios visibles en películas distintas
    @Query("SELECT COUNT(DISTINCT c.movieId) FROM Comment c WHERE c.user.id = :userId AND c.moderationStatus = 'APPROVED'")
    long countDistinctMoviesCommentedByUser(@Param("userId") Long userId);

    // ── Ocultamientos por usuario por película ────────────────────────────────

    // Cuenta cuántos comentarios ocultó el usuario sobre una película específica
    @Query("SELECT COUNT(c) FROM Comment c " +
            "WHERE c.user.id = :userId " +
            "AND c.movieId = :movieId " +
            "AND c.moderationStatus = 'HIDDEN_BY_USER'")
    long countHiddenByUserAndMovie(@Param("userId") Long userId,
                                   @Param("movieId") Long movieId);

    // GIFs en comentarios
    long countByHasGifTrue();

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.hasGif = true AND c.createdAt BETWEEN :start AND :end")
    long countGifsInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}