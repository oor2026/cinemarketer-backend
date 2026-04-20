package com.example.demo.domain.reward;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface RewardRepository extends JpaRepository<Reward, Long> {

    // Buscar premios disponibles (activos, con stock, no expirados)
    @Query("SELECT r FROM Reward r WHERE r.active = true AND r.stock > 0 AND " +
            "(r.expiryDate IS NULL OR r.expiryDate >= :today)")
    List<Reward> findAvailableRewards(@Param("today") LocalDate today);

    // Buscar por tipo de premio
    List<Reward> findByRewardType(RewardType rewardType);

    // Buscar premios que un usuario puede canjear con sus puntos
    @Query("SELECT r FROM Reward r WHERE r.pointsRequired <= :userPoints AND " +
            "r.active = true AND r.stock > 0 AND " +
            "(r.expiryDate IS NULL OR r.expiryDate >= :today)")
    List<Reward> findAffordableRewards(@Param("userPoints") int userPoints,
                                       @Param("today") LocalDate today);

    // Buscar premios por nombre (búsqueda)
    List<Reward> findByNameContainingIgnoreCase(String name);

    // Buscar premios próximos a vencer
    @Query("SELECT r FROM Reward r WHERE r.expiryDate BETWEEN :start AND :end")
    List<Reward> findRewardsExpiringBetween(@Param("start") LocalDate start,
                                            @Param("end") LocalDate end);

    // Buscar premios agotados
    List<Reward> findByStockLessThanEqual(Integer minStock);

    // Buscar premios ordenados por puntos requeridos (menor a mayor)
    List<Reward> findByActiveTrueOrderByPointsRequiredAsc();

    // Buscar premios más populares (los más canjeados - lo implementaremos con Redemption después)
    @Query("SELECT r, COUNT(red) as canjes FROM Reward r LEFT JOIN Redemption red ON r.id = red.reward.id " +
            "GROUP BY r.id ORDER BY canjes DESC")
    List<Object[]> findMostPopularRewards();

    // Verificar si existe un premio con el mismo nombre
    boolean existsByNameIgnoreCase(String name);

    // Contar premios activos
    long countByActiveTrue();

    // Contar premios con stock = 0
    @Query("SELECT COUNT(r) FROM Reward r WHERE r.stock = 0")
    long countByStockZero();

    // Contar premios creados en un período
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Top premios más populares (más canjes)
    @Query("SELECT r.id, r.name, COUNT(rd) as redemptionCount " +
            "FROM Reward r LEFT JOIN Redemption rd ON r.id = rd.reward.id " +
            "GROUP BY r.id, r.name ORDER BY redemptionCount DESC")
    List<Map<String, Object>> findTopRewardsByRedemptions(Pageable pageable);

    // Promedio de puntos requeridos por premio
    @Query("SELECT AVG(r.pointsRequired) FROM Reward r WHERE r.active = true")
    Double getAveragePointsRequired();
}