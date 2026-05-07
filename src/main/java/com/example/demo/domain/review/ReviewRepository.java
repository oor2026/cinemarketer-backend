package com.example.demo.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Buscar reviews por usuario
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Review> findByUserId(Long userId, Pageable pageable);

    // Buscar reviews de una película
    List<Review> findByReviewTypeAndTargetIdOrderByCreatedAtDesc(ReviewType reviewType, Long targetId);

    Page<Review> findByReviewTypeAndTargetId(ReviewType reviewType, Long targetId, Pageable pageable);

    // Buscar reviews de un cine
    List<Review> findByReviewTypeAndTargetIdAndActiveTrue(ReviewType reviewType, Long targetId);

    // Buscar el voto de un usuario específico para una película/cine
    Optional<Review> findByUserIdAndReviewTypeAndTargetId(Long userId, ReviewType reviewType, Long targetId);

    // Verificar si un usuario ya votó
    boolean existsByUserIdAndReviewTypeAndTargetId(Long userId, ReviewType reviewType, Long targetId);

    // Contar likes y dislikes
    @Query("SELECT COUNT(r) FROM Review r WHERE r.reviewType = :reviewType AND r.targetId = :targetId AND r.vote = :voteType")
    long countByReviewTypeAndTargetIdAndVote(@Param("reviewType") ReviewType reviewType,
                                             @Param("targetId") Long targetId,
                                             @Param("voteType") VoteType voteType);

    // Obtener todas las reviews de un usuario con información del contenido
    @Query("SELECT r FROM Review r WHERE r.user.id = :userId ORDER BY r.createdAt DESC")
    List<Review> findUserReviewsWithContent(@Param("userId") Long userId);

    // Buscar reviews recientes (feed)
    @Query("SELECT r FROM Review r WHERE r.active = true ORDER BY r.createdAt DESC")
    List<Review> findRecentReviews(Pageable pageable);

    // Buscar reviews por período de tiempo
    List<Review> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    // Estadísticas: reviews por tipo en un rango de fechas
    @Query("SELECT r.reviewType, COUNT(r) FROM Review r WHERE r.createdAt BETWEEN :start AND :end GROUP BY r.reviewType")
    List<Object[]> countByTypeAndDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Eliminar voto de usuario (cuando cambia de opinión)
    void deleteByUserIdAndReviewTypeAndTargetId(Long userId, ReviewType reviewType, Long targetId);

    // Contar total de votos (likes + dislikes)
    long countByReviewTypeAndTargetId(ReviewType reviewType, Long targetId);

    long countByUserId(Long userId);

    void deleteByUser(com.example.demo.domain.user.User user);

    // Contar votos por tipo en un período
    @Query("SELECT COUNT(r) FROM Review r WHERE r.vote = :voteType AND r.createdAt BETWEEN :start AND :end")
    long countByVoteTypeInPeriod(@Param("voteType") VoteType voteType,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    // Contar votos totales en un período
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Top películas más votadas
    @Query("SELECT r.targetId as id, COALESCE(m.title, 'Película ' || r.targetId) as title, " +
            "COUNT(r) as votes, " +
            "SUM(CASE WHEN r.vote = 'LIKE' THEN 1 ELSE 0 END) as likes, " +
            "SUM(CASE WHEN r.vote = 'DISLIKE' THEN 1 ELSE 0 END) as dislikes " +
            "FROM Review r LEFT JOIN Movie m ON r.targetId = m.id " +  // ← LEFT JOIN
            "WHERE r.reviewType = 'MOVIE' AND r.createdAt BETWEEN :start AND :end " +
            "GROUP BY r.targetId, m.title ORDER BY votes DESC")
    List<Map<String, Object>> findTopMoviesByVotes(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end,
                                                   Pageable pageable);

    // Top usuarios que más votan
    @Query("SELECT u.id as id, u.name as name, COUNT(r) as votes " +
            "FROM Review r JOIN User u ON r.user.id = u.id " +
            "WHERE r.createdAt BETWEEN :start AND :end " +
            "GROUP BY u.id, u.name ORDER BY votes DESC")
    List<Map<String, Object>> findTopUsersByVotes(@Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end,
                                                  Pageable pageable);

    // Tendencia diaria de votos
    @Query("SELECT FUNCTION('DATE', r.createdAt) as date, COUNT(r) as count " +
            "FROM Review r WHERE r.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', r.createdAt) ORDER BY date")
    List<Object[]> getDailyVoteCount(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    // Distribución por día de la semana
    @Query(value = "SELECT EXTRACT(DOW FROM created_at) as dayOfWeek, COUNT(id) as count " +
            "FROM reviews WHERE created_at BETWEEN :start AND :end " +
            "GROUP BY EXTRACT(DOW FROM created_at) ORDER BY dayOfWeek",
            nativeQuery = true)
    List<Object[]> getVoteDistributionByWeekday(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    // Distribución por hora del día
    @Query(value = "SELECT EXTRACT(HOUR FROM created_at) as hour, COUNT(id) as count " +
            "FROM reviews WHERE created_at BETWEEN :start AND :end " +
            "GROUP BY EXTRACT(HOUR FROM created_at) ORDER BY hour",
            nativeQuery = true)
    List<Object[]> getVoteDistributionByHour(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    // Usuarios distintos que votaron
    @Query("SELECT COUNT(DISTINCT r.user.id) FROM Review r WHERE r.createdAt BETWEEN :start AND :end")
    long countDistinctUsersInPeriod(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    // Usuarios distintos que votaron (sin período)
    @Query("SELECT COUNT(DISTINCT r.user.id) FROM Review r")
    long countDistinctUsers();

    long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    long countByUserIdAndCommentIsNotNullAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}