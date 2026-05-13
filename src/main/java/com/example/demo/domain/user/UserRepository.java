package com.example.demo.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ==============================================
    // MÉTODOS EXISTENTES (se mantienen intactos)
    // ==============================================

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String token);

    Optional<User> findByResetPasswordToken(String token);

    List<User> findByActiveTrue();

    List<User> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByActiveTrue();

    long countByEmailVerifiedTrue();

    long countBySuspendedTrue();

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByAvailablePointsGreaterThan(int points);

    @Query("SELECT COUNT(u) FROM User u WHERE u.lastLoginAt < :date OR u.lastLoginAt IS NULL")
    long countInactiveSince(@Param("date") LocalDateTime date);

    boolean existsByDni(String dni);

    // ==============================================
    // NUEVOS MÉTODOS PARA FILTRAR POR NIVEL
    // ==============================================

    /**
     * Busca usuarios por nivel específico
     */
    List<User> findByLevel(UserLevel level);

    /**
     * Busca usuarios por nivel con paginación
     */
    Page<User> findByLevel(UserLevel level, Pageable pageable);

    /**
     * Cuenta usuarios por nivel
     */
    long countByLevel(UserLevel level);

    /**
     * Busca usuarios cuyo nivel sea igual o superior a uno dado
     * (usando CASE para simular ordinal)
     */
    @Query("SELECT u FROM User u WHERE " +
            "CASE u.level " +
            "   WHEN 'AMATEUR' THEN 0 " +
            "   WHEN 'COLABORADOR' THEN 1 " +
            "   WHEN 'CRITICO' THEN 2 " +
            "   WHEN 'JURADO_EXPERTO' THEN 3 END >= :minLevelOrdinal")
    List<User> findByLevelMin(@Param("minLevelOrdinal") int minLevelOrdinal);

    /**
     * Versión con UserLevel del método anterior
     */
    default List<User> findByLevelMin(UserLevel minLevel) {
        return findByLevelMin(minLevel.ordinal());
    }

    /**
     * Busca usuarios cuyo nivel sea igual o inferior a uno dado
     */
    @Query("SELECT u FROM User u WHERE " +
            "CASE u.level " +
            "   WHEN 'AMATEUR' THEN 0 " +
            "   WHEN 'COLABORADOR' THEN 1 " +
            "   WHEN 'CRITICO' THEN 2 " +
            "   WHEN 'JURADO_EXPERTO' THEN 3 END <= :maxLevelOrdinal")
    List<User> findByLevelMax(@Param("maxLevelOrdinal") int maxLevelOrdinal);

    /**
     * Versión con UserLevel del método anterior
     */
    default List<User> findByLevelMax(UserLevel maxLevel) {
        return findByLevelMax(maxLevel.ordinal());
    }

    /**
     * Busca usuarios con nivel entre dos valores (inclusive)
     */
    @Query("SELECT u FROM User u WHERE " +
            "CASE u.level " +
            "   WHEN 'AMATEUR' THEN 0 " +
            "   WHEN 'COLABORADOR' THEN 1 " +
            "   WHEN 'CRITICO' THEN 2 " +
            "   WHEN 'JURADO_EXPERTO' THEN 3 END BETWEEN :minOrdinal AND :maxOrdinal")
    List<User> findByLevelBetween(@Param("minOrdinal") int minOrdinal,
                                  @Param("maxOrdinal") int maxOrdinal);

    /**
     * Versión con UserLevel del método anterior
     */
    default List<User> findByLevelBetween(UserLevel minLevel, UserLevel maxLevel) {
        return findByLevelBetween(minLevel.ordinal(), maxLevel.ordinal());
    }

    // ==============================================
    // ESTADÍSTICAS POR NIVEL
    // ==============================================

    /**
     * Obtiene el conteo de usuarios agrupados por nivel
     * Retorna una lista de arrays donde cada array contiene [nivel, cantidad]
     */
    @Query("SELECT u.level, COUNT(u) FROM User u GROUP BY u.level ORDER BY u.level")
    List<Object[]> countUsersByLevel();

    /**
     * Obtiene la distribución de niveles (más útil que el promedio)
     */
    @Query("SELECT u.level, COUNT(u) FROM User u GROUP BY u.level")
    List<Object[]> getLevelDistribution();

    /**
     * Obtiene los usuarios con nivel más alto (JURADO_EXPERTO y CRITICO)
     */
    @Query("SELECT u FROM User u WHERE u.level = 'JURADO_EXPERTO' OR u.level = 'CRITICO' " +
            "ORDER BY CASE u.level " +
            "   WHEN 'JURADO_EXPERTO' THEN 1 " +
            "   WHEN 'CRITICO' THEN 2 END, u.totalRedeemedPoints DESC")
    List<User> findTopLevelUsers(Pageable pageable);

    // ==============================================
    // ACTUALIZACIONES MASIVAS (solo para admin)
    // ==============================================

    /**
     * Actualiza el nivel de todos los usuarios según sus puntos
     * (Útil para recalcular niveles después de cambios en la lógica)
     */
    @Modifying
    @Query("UPDATE User u SET u.level = " +
            "CASE " +
            "  WHEN u.totalRedeemedPoints >= 60000 THEN 'JURADO_EXPERTO' " +
            "  WHEN u.totalRedeemedPoints >= 40000 THEN 'CRITICO' " +
            "  WHEN u.totalRedeemedPoints >= 20000 THEN 'COLABORADOR' " +
            "  ELSE 'AMATEUR' " +
            "END, " +
            "u.levelUpdatedAt = CURRENT_TIMESTAMP, " +
            "u.updatedAt = CURRENT_TIMESTAMP")
    void recalculateAllLevels();

    // ==============================================
    // BÚSQUEDAS COMBINADAS (nivel + otros criterios)
    // ==============================================

    /**
     * Busca usuarios activos por nivel
     */
    List<User> findByLevelAndActiveTrue(UserLevel level);

    /**
     * Busca usuarios por nivel que tengan puntos mayores a un mínimo
     */
    List<User> findByLevelAndAvailablePointsGreaterThanEqual(UserLevel level, int minPoints);

    /**
     * Busca usuarios que hayan actualizado su nivel en un rango de fechas
     */
    List<User> findByLevelUpdatedAtBetween(LocalDateTime start, LocalDateTime end);

    // ==============================================
    // PARA EL ADMIN: USUARIOS QUE PUEDEN SUBIR DE NIVEL
    // ==============================================

    /**
     * Encuentra usuarios que están cerca de subir de nivel
     * (puntos suficientes para el siguiente nivel pero nivel no actualizado)
     */
    @Query("SELECT u FROM User u WHERE " +
            "((u.level = 'AMATEUR' AND u.totalRedeemedPoints >= 20000) OR " +
            " (u.level = 'COLABORADOR' AND u.totalRedeemedPoints >= 40000) OR " +
            " (u.level = 'CRITICO' AND u.totalRedeemedPoints >= 60000)) " +
            "AND u.active = true")
    List<User> findUsersEligibleForLevelUp();

    /**
     * Cuenta usuarios por nivel (versión simplificada para dashboard)
     */
    @Query("SELECT u.level, COUNT(u) FROM User u WHERE u.active = true GROUP BY u.level")
    List<Object[]> countActiveUsersByLevel();

    /**
     * Busca usuarios de un nivel específico con puntos menores a un valor
     * Útil para encontrar inconsistencias (ej: Jurado Experto con pocos puntos)
     */
    List<User> findByLevelAndTotalRedeemedPointsLessThan(UserLevel level, int points);

    /**
     * Busca usuarios por nombre o email que contengan el texto (case insensitive)
     * Útil para el buscador en el panel de admin
     */
    Page<User> findByNameContainingOrEmailContaining(String name, String email, Pageable pageable);

    /**
     * Cuenta usuarios suspendidos
     */
    long countBySuspended(boolean suspended);

    /**
     * Cuenta usuarios con email verificado
     */
    long countByEmailVerified(boolean verified);

    @Modifying
    @Query("UPDATE User u SET u.avatarUrl = null WHERE u.avatarUrl = :imageUrl")
    void resetAvatarUrlForUsers(@Param("imageUrl") String imageUrl);

    Page<User> findBySuspendedFalseAndActiveTrue(Pageable pageable);

    Page<User> findBySuspendedTrue(Pageable pageable);
}