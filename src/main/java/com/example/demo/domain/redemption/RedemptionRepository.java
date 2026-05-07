package com.example.demo.domain.redemption;

import com.example.demo.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    // Buscar canjes por usuario
    List<Redemption> findByUserIdOrderByRedemptionDateDesc(Long userId);

    Page<Redemption> findByUserId(Long userId, Pageable pageable);

    // Buscar canjes por premio
    List<Redemption> findByRewardIdOrderByRedemptionDateDesc(Long rewardId);

    // Buscar por estado
    List<Redemption> findByStatusAndDeletedFalse(RedemptionStatus status);

    // Buscar canjes pendientes por usuario
    List<Redemption> findByUserIdAndStatus(Long userId, RedemptionStatus status);

    // Buscar canjes expirados
    @Query("SELECT r FROM Redemption r WHERE r.expiresAt < :now AND r.status = 'PENDING'")
    List<Redemption> findExpiredRedemptions(@Param("now") LocalDateTime now);

    // eliminar visualmente el registro en el admin
    Page<Redemption> findByDeletedFalse(Pageable pageable);

    // Contar canjes por usuario
    long countByUserId(Long userId);

    // Sumar puntos gastados por usuario
    @Query("SELECT COALESCE(SUM(r.pointsSpent), 0) FROM Redemption r WHERE r.user.id = :userId")
    int getTotalPointsSpentByUser(@Param("userId") Long userId);

    // Buscar canjes recientes
    List<Redemption> findTop10ByOrderByRedemptionDateDesc();

    // Estadísticas: canjes por período
    @Query("SELECT DATE(r.redemptionDate), COUNT(r) FROM Redemption r " +
            "WHERE r.redemptionDate BETWEEN :start AND :end GROUP BY DATE(r.redemptionDate)")
    List<Object[]> getRedemptionsPerDay(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    // Verificar si un usuario ya canjeó un premio específico
    boolean existsByUserIdAndRewardId(Long userId, Long rewardId);

    void deleteByUser(com.example.demo.domain.user.User user);

    // MÉTODOS PARA ESTADÍSTICAS
    long countByRedemptionDateBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(r) FROM Redemption r WHERE r.status = :status AND r.redemptionDate BETWEEN :start AND :end")
    long countByStatusInPeriod(@Param("status") RedemptionStatus status,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(r.pointsSpent), 0) FROM Redemption r WHERE r.redemptionDate BETWEEN :start AND :end")
    long sumPointsSpentInPeriod(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Query("SELECT r.reward.id as id, r.reward.name as name, COUNT(r) as count " +
            "FROM Redemption r WHERE r.redemptionDate BETWEEN :start AND :end " +
            "GROUP BY r.reward.id, r.reward.name ORDER BY count DESC")
    List<Map<String, Object>> findTopRewardsByRedemptions(@Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end,
                                                          Pageable pageable);

    // Para el embudo de conversión
    @Query("SELECT COUNT(DISTINCT r.user.id) FROM Redemption r")
    long countDistinctUsers();

    @Query(value = "SELECT COUNT(DISTINCT user_id) FROM (SELECT user_id, COUNT(*) as cnt FROM redemptions GROUP BY user_id HAVING COUNT(*) >= 2) as multi", nativeQuery = true)
    long countUsersWithMultipleRedemptions();
}