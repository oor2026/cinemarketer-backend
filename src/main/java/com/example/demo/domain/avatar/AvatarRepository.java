package com.example.demo.domain.avatar;

import com.example.demo.domain.user.UserLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la entidad Avatar
 * Proporciona métodos para acceder a los avatares predefinidos
 */
@Repository
public interface AvatarRepository extends JpaRepository<Avatar, Long> {

    // ==============================================
    // BÚSQUEDAS BÁSICAS
    // ==============================================

    /**
     * Busca todos los avatares activos ordenados por sortOrder
     */
    List<Avatar> findByActiveTrueOrderBySortOrderAsc();

    /**
     * Busca avatares por categoría
     */
    List<Avatar> findByCategoryAndActiveTrueOrderBySortOrderAsc(String category);

    /**
     * Busca avatares por nivel requerido
     */
    List<Avatar> findByRequiredLevelAndActiveTrueOrderBySortOrderAsc(UserLevel level);

    /**
     * Busca avatares que no tienen nivel requerido (disponibles para todos)
     */
    List<Avatar> findByRequiredLevelIsNullAndActiveTrueOrderBySortOrderAsc();

    /**
     * Busca el avatar por defecto
     */
    Optional<Avatar> findByIsDefaultTrueAndActiveTrue();

    // ==============================================
    // BÚSQUEDAS COMBINADAS PARA GALERÍA
    // ==============================================

    /**
     * Obtiene todos los avatares disponibles para un nivel específico
     * Incluye:
     * - Avatares sin nivel requerido (para todos)
     * - Avatares con nivel requerido igual o inferior al del usuario
     */
    @Query("SELECT a FROM Avatar a WHERE a.active = true AND " +
            "(a.requiredLevel IS NULL OR " +
            "CASE a.requiredLevel " +
            "   WHEN 'AMATEUR' THEN 0 " +
            "   WHEN 'COLABORADOR' THEN 1 " +
            "   WHEN 'CRITICO' THEN 2 " +
            "   WHEN 'JURADO_EXPERTO' THEN 3 END <= :userLevelOrdinal) " +
            "ORDER BY a.sortOrder ASC")
    List<Avatar> findAvailablesForUserLevel(@Param("userLevelOrdinal") int userLevelOrdinal);

    /**
     * Versión con UserLevel directamente
     */
    default List<Avatar> findAvailablesForUserLevel(UserLevel userLevel) {
        return findAvailablesForUserLevel(userLevel.ordinal());
    }

    // ==============================================
    // BÚSQUEDAS POR CATEGORÍA Y NIVEL
    // ==============================================

    /**
     * Busca avatares por categoría disponibles para un nivel
     */
    @Query("SELECT a FROM Avatar a WHERE a.active = true AND a.category = :category AND " +
            "(a.requiredLevel IS NULL OR " +
            "CASE a.requiredLevel " +
            "   WHEN 'AMATEUR' THEN 0 " +
            "   WHEN 'COLABORADOR' THEN 1 " +
            "   WHEN 'CRITICO' THEN 2 " +
            "   WHEN 'JURADO_EXPERTO' THEN 3 END <= :userLevelOrdinal) " +
            "ORDER BY a.sortOrder ASC")
    List<Avatar> findByCategoryAndUserLevel(@Param("category") String category,
                                            @Param("userLevelOrdinal") int userLevelOrdinal);

    // ==============================================
    // AVATARES POR DEFECTO
    // ==============================================

    /**
     * Obtiene el avatar por defecto para una categoría específica
     */
    @Query("SELECT a FROM Avatar a WHERE a.active = true AND " +
            "a.category = :category AND a.isDefault = true")
    Optional<Avatar> findDefaultByCategory(@Param("category") String category);

    // ==============================================
    // CONTEO Y ESTADÍSTICAS
    // ==============================================

    /**
     * Cuenta cuántos avatares activos hay por categoría
     */
    @Query("SELECT a.category, COUNT(a) FROM Avatar a WHERE a.active = true GROUP BY a.category")
    List<Object[]> countByCategory();

    /**
     * Cuenta cuántos avatares activos hay por nivel requerido
     */
    @Query("SELECT a.requiredLevel, COUNT(a) FROM Avatar a WHERE a.active = true GROUP BY a.requiredLevel")
    List<Object[]> countByRequiredLevel();

    // ==============================================
    // BÚSQUEDA POR TEXTO (PARA ADMIN)
    // ==============================================

    /**
     * Busca avatares por nombre (para autocompletado en admin)
     */
    List<Avatar> findByNameContainingIgnoreCaseAndActiveTrue(String name);

    // ==============================================
    // VERIFICACIONES
    // ==============================================

    /**
     * Verifica si existe un avatar con el mismo nombre (para evitar duplicados)
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Verifica si existe algún avatar por defecto en una categoría
     */
    boolean existsByCategoryAndIsDefaultTrue(String category);

    /**
     * Cuenta avatares activos
     */
    long countByActiveTrue();

    /**
     * Para obtener estadísticas de uso (avatares usados por usuarios)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.avatarUrl IN (SELECT a.imageUrl FROM Avatar a)")
    long countUsersUsingPredefinedAvatar();

    Optional<Avatar> findByImageUrl(String imageUrl);
}