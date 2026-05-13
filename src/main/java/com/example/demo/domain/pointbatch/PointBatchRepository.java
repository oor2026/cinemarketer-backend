package com.example.demo.domain.pointbatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PointBatchRepository extends JpaRepository<PointBatch, Long> {

    /**
     * Lotes activos del usuario ordenados por fecha de liberación (FIFO)
     */
    List<PointBatch> findByUserIdAndExpiredFalseAndRemainingPointsGreaterThanOrderByReleasedAtAsc(
            Long userId, int minPoints);

    /**
     * Lotes vencidos que aún no fueron marcados como expirados
     */
    @Query("SELECT b FROM PointBatch b WHERE b.expired = false " +
           "AND b.expiresAt IS NOT NULL AND b.expiresAt <= :now " +
           "AND b.remainingPoints > 0")
    List<PointBatch> findExpiredBatches(@Param("now") LocalDateTime now);

    /**
     * Total de puntos disponibles de un usuario (suma de remainingPoints activos)
     */
    @Query("SELECT COALESCE(SUM(b.remainingPoints), 0) FROM PointBatch b " +
           "WHERE b.user.id = :userId AND b.expired = false AND b.remainingPoints > 0")
    int sumAvailablePointsByUserId(@Param("userId") Long userId);

    /**
     * Lotes activos de un usuario con puntos disponibles (FIFO)
     */
    default List<PointBatch> findActiveBatchesByUserId(Long userId) {
        return findByUserIdAndExpiredFalseAndRemainingPointsGreaterThanOrderByReleasedAtAsc(userId, 0);
    }
}
