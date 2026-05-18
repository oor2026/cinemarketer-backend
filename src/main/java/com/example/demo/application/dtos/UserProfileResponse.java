package com.example.demo.application.dtos;

import com.example.demo.domain.user.UserLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String email;
    private String name;
    private String role;

    /**
     * ID de Google si la cuenta está vinculada con Google OAuth
     * Null si es cuenta registrada con email/contraseña
     */
    private String googleId;

    /** Puntos disponibles para canjear (liberados, no vencidos) */
    private int availablePoints;

    /** Puntos acumulados en el mes en curso, aún no liberados */
    private int accumulatedPoints;

    /** Total histórico de puntos canjeados (base para insignias) */
    private int totalRedeemedPoints;

    /** Puntos próximos a vencer en los próximos 30 días (solo FREE) */
    private int expiringPoints;

    /** @deprecated Usar availablePoints */
    @Deprecated
    private int totalPoints;

    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private int reviewsCount;
    private int redemptionsCount;
    private int commentsCount;
    private String dni;
    private String phone;
    private String avatarName;

    /**
     * URL del avatar actual del usuario
     */
    private String avatarUrl;

    /**
     * Nivel actual del usuario (AMATEUR, COLABORADOR, CRITICO, JURADO_EXPERTO)
     */
    private UserLevel level;

    /**
     * Nombre legible del nivel para mostrar en UI
     * Ej: "Amateur", "Colaborador", "Crítico", "Jurado Experto"
     */
    private String levelDisplayName;

    /**
     * Emoji representativo del nivel
     * Ej: "🟢", "🔵", "🟣", "🏆"
     */
    private String levelEmoji;

    /**
     * Fecha de la última actualización del nivel
     */
    private LocalDateTime levelUpdatedAt;

    /**
     * Nivel siguiente al actual (si existe)
     */
    private UserLevel nextLevel;

    /**
     * Nombre legible del siguiente nivel
     */
    private String nextLevelDisplayName;

    /**
     * Puntos necesarios para alcanzar el siguiente nivel
     */
    private Integer pointsToNextLevel;

    /**
     * Porcentaje de progreso hacia el siguiente nivel (0-100)
     */
    private Double levelProgress;

    /**
     * Indica si el usuario puede subir de nivel actualmente
     */
    private Boolean canLevelUp;

    /**
     * Indica si el usuario tiene suscripción premium activa
     */
    private boolean isPremium;

    /**
     * Fecha hasta la que tiene acceso premium
     */
    private LocalDateTime premiumUntil;
}