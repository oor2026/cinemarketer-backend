package com.example.demo.domain.pointtransaction;

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
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    // Historial completo del usuario paginado
    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Filtrar por tipo (EARNED o SPENT)
    Page<PointTransaction> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, PointTransactionType type, Pageable pageable);

    // Total de puntos ganados por usuario
    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.user.id = :userId AND t.type = 'EARNED'")
    int getTotalEarned(@Param("userId") Long userId);

    // Total de puntos gastados por usuario
    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.user.id = :userId AND t.type = 'SPENT'")
    int getTotalSpent(@Param("userId") Long userId);

    // Puntos ganados este mes
    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.user.id = :userId " +
           "AND t.type = 'EARNED' AND MONTH(t.createdAt) = MONTH(CURRENT_DATE) AND YEAR(t.createdAt) = YEAR(CURRENT_DATE)")
    int getEarnedThisMonth(@Param("userId") Long userId);

    // Suma de puntos ganados en un período
    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.type = 'EARNED' AND t.createdAt BETWEEN :start AND :end")
    long sumEarnedInPeriod(@Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);

    // Suma de puntos gastados en un período
    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.type = 'SPENT' AND t.createdAt BETWEEN :start AND :end")
    long sumSpentInPeriod(@Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    // Top acciones más puntuadas en un período
    @Query("SELECT t.action as action, COUNT(t) as count, SUM(t.points) as totalPoints " +
            "FROM PointTransaction t WHERE t.createdAt BETWEEN :start AND :end " +
            "GROUP BY t.action ORDER BY count DESC")
    List<Map<String, Object>> findTopActionsInPeriod(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     Pageable pageable);

    // Total de transacciones en un período
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Transacciones por tipo en un período
    long countByTypeAndCreatedAtBetween(PointTransactionType type, LocalDateTime start, LocalDateTime end);

    // Promedio de puntos por transacción en un período
    @Query("SELECT AVG(t.points) FROM PointTransaction t WHERE t.createdAt BETWEEN :start AND :end")
    Double getAveragePointsInPeriod(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.user.id = :userId AND t.type = :type")
    int sumPointsByUserAndType(@Param("userId") Long userId, @Param("type") PointTransactionType type);
}
