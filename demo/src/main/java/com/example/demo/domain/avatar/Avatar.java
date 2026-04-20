package com.example.demo.domain.avatar;

import com.example.demo.domain.user.UserLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad que representa los avatares predefinidos que los usuarios pueden elegir
 * Incluye tanto avatares genéricos como avatares temáticos por nivel
 */
@Entity
@Table(name = "avatars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avatar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre descriptivo del avatar
     * Ej: "Crítico Clásico", "Amateur Divertido", "Jurado Estrella"
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Categoría del avatar para facilitar la organización en la galería
     * Puede ser: "general", "amateur", "colaborador", "critico", "jurado"
     */
    @Column(length = 50)
    private String category;

    /**
     * Nivel mínimo requerido para usar este avatar
     * Si es null, está disponible para todos los niveles
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "required_level")
    private UserLevel requiredLevel;

    /**
     * URL de la imagen del avatar (tamaño completo)
     */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /**
     * URL de la miniatura del avatar (opcional, para galerías)
     */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /**
     * Indica si el avatar está activo y disponible para los usuarios
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Orden de aparición en la galería (menor número = primero)
     */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /**
     * Indica si es un avatar por defecto (no se puede eliminar)
     */
    @Column(name = "is_default")
    private boolean isDefault = false;

    /**
     * Fecha de creación del registro
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Fecha de última actualización
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==============================================
    // MÉTODOS DE CICLO DE VIDA
    // ==============================================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==============================================
    // MÉTODOS DE UTILIDAD
    // ==============================================

    /**
     * Verifica si el avatar está disponible para un nivel específico
     */
    public boolean isAvailableForLevel(UserLevel userLevel) {
        if (!active) return false;
        if (requiredLevel == null) return true;
        return userLevel.ordinal() >= requiredLevel.ordinal();
    }

    /**
     * Obtiene la URL a mostrar (usa miniatura si existe, sino la imagen completa)
     */
    public String getDisplayUrl() {
        return thumbnailUrl != null ? thumbnailUrl : imageUrl;
    }

    /**
     * Retorna una copia del avatar para usar como DTO básico
     */
    public AvatarBasicDto toBasicDto() {
        return new AvatarBasicDto(this.id, this.name, this.getDisplayUrl(), this.requiredLevel);
    }

    // ==============================================
    // INNER CLASS PARA DTO BÁSICO
    // ==============================================

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AvatarBasicDto {
        private Long id;
        private String name;
        private String imageUrl;
        private UserLevel requiredLevel;
    }
}