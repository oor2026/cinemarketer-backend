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
            "AND t.type = 'EARNED' " +
            "AND EXTRACT(MONTH FROM t.createdAt) = EXTRACT(MONTH FROM CURRENT_DATE) " +
            "AND EXTRACT(YEAR FROM t.createdAt) = EXTRACT(YEAR FROM CURRENT_DATE)")
    int getEarnedThisMonth(@Param("userId") Long userId);

    // Puntos canjeados este mes
    @Query("SELECT COALESCE(SUM(t.points), 0) FROM PointTransaction t WHERE t.user.id = :userId " +
            "AND t.type = 'SPENT' " +
            "AND EXTRACT(MONTH FROM t.createdAt) = EXTRACT(MONTH FROM CURRENT_DATE) " +
            "AND EXTRACT(YEAR FROM t.createdAt) = EXTRACT(YEAR FROM CURRENT_DATE)")
    int getRedeemedThisMonth(@Param("userId") Long userId);

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

    // Buscar transaccion especifica por usuario, accion y referencia (para reversiones)
    @Query("SELECT t FROM PointTransaction t WHERE t.user.id = :userId AND t.action = :action AND t.referenceId = :referenceId ORDER BY t.createdAt DESC")
    List<PointTransaction> findByUserIdAndActionAndReferenceId(
            @Param("userId") Long userId,
            @Param("action") com.example.demo.domain.point.PointAction action,
            @Param("referenceId") Long referenceId);

    // Filtrar por tipo y acción específica (ej: solo canjes)
    Page<PointTransaction> findByUserIdAndTypeAndActionOrderByCreatedAtDesc(
            Long userId, PointTransactionType type,
            com.example.demo.domain.point.PointAction action, Pageable pageable);

    long countByUserIdAndAction(Long userId, com.example.demo.domain.point.PointAction action);

    // Última transacción de TRIVIA_ANSWER del usuario en el rango de hoy —
    // para el upsert que agrega todos los aciertos del día en un solo registro.
    java.util.Optional<PointTransaction> findFirstByUserIdAndActionAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, com.example.demo.domain.point.PointAction action,
            LocalDateTime start, LocalDateTime end);

    @Query("SELECT t.action as action, COUNT(t) as count, SUM(t.points) as totalPoints " +
            "FROM PointTransaction t WHERE t.type = 'EARNED' AND t.createdAt BETWEEN :start AND :end " +
            "GROUP BY t.action ORDER BY totalPoints DESC")
    List<Map<String, Object>> findPointsDistributionByAction(@Param("start") LocalDateTime start,
                                                             @Param("end") LocalDateTime end);
}