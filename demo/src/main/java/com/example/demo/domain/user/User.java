package com.example.demo.domain.user;

import com.example.demo.domain.redemption.Redemption;
import com.example.demo.domain.review.Review;
import com.example.demo.domain.sweepstake.SweepstakeEntry;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
public class User {

    // id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // name
    @Column(nullable = false, length = 100)
    private String name;

    // email
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // password
    @Column(nullable = false)
    private String password;  // Almacenará el hash BCrypt

    // emailVerified
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    // verificationToken
    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    // resetPasswordToken
    @Column(name = "reset_password_token", length = 255)
    private String resetPasswordToken;

    // role
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;  // Por defecto USER

    // totalPoints
    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

    // createdAt
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // updatedAt
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // lastLoginAt
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // profileImageUrl
    @Column(name = "profile_image_url")
    private String profileImageUrl;

    // dni
    @Column(name = "dni", unique = true, length = 20)
    private String dni;

    // phone
    @Column(name = "phone", length = 30)
    private String phone;

    // active
    private boolean active = true;  // Usuario activo/inactivo

    @Column(nullable = false)
    private Boolean suspended = false;  // false = activo, true = suspendido

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;    // Razón de la suspensión

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    // reviews
    @OneToMany(mappedBy = "user")
    private List<Review> reviews;

    // redemptions
    @OneToMany(mappedBy = "user")
    private List<Redemption> redemptions;

    // sweepstakeEntries
    @OneToMany(mappedBy = "user")
    private List<SweepstakeEntry> sweepstakeEntries;

    // ==============================================
    // NUEVOS CAMPOS PARA AVATAR Y NIVEL (AGREGADOS)
    // ==============================================

    /**
     * URL del avatar seleccionado por el usuario
     * Puede ser un avatar predefinido o una imagen subida
     */
    @Column(name = "avatar_url")
    private String avatarUrl;

    /**
     * Nivel actual del usuario
     * Por defecto: AMATEUR
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_level", nullable = false)
    private UserLevel level = UserLevel.AMATEUR;

    /**
     * Fecha de la última actualización del nivel
     * Útil para auditoría y para evitar recálculos innecesarios
     */
    @Column(name = "level_updated_at")
    private LocalDateTime levelUpdatedAt;

    // ==============================================
    // SUSCRIPCIÓN PREMIUM
    // ==============================================

    @Column(name = "is_premium", nullable = false)
    private boolean premium = false;

    @Column(name = "premium_until")
    private LocalDateTime premiumUntil;

    // ==============================================
    // NUEVOS MÉTODOS PARA GESTIÓN DE AVATAR
    // ==============================================

    /**
     * Actualiza el avatar del usuario
     */
    public void updateAvatar(String newAvatarUrl) {
        this.avatarUrl = newAvatarUrl;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Elimina el avatar personalizado (vuelve al avatar por defecto)
     */
    public void removeAvatar() {
        this.avatarUrl = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Obtiene la URL del avatar a mostrar (prioriza avatarUrl, luego profileImageUrl)
     */
    public String getEffectiveAvatarUrl() {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            return avatarUrl;
        }
        return profileImageUrl;  // fallback al campo anterior
    }

    // ==============================================
    // NUEVOS MÉTODOS PARA GESTIÓN DE NIVEL
    // ==============================================

    /**
     * Actualiza el nivel del usuario si corresponde según sus puntos
     * Retorna true si hubo cambio de nivel
     */
    public boolean updateLevelBasedOnPoints() {
        UserLevel newLevel = UserLevel.getLevelByPoints(this.totalPoints);
        if (this.level != newLevel) {
            this.level = newLevel;
            this.levelUpdatedAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }

    /**
     * Asigna un nivel manualmente (solo para admin)
     */
    public void setLevelManually(UserLevel newLevel) {
        if (newLevel != null) {
            this.level = newLevel;
            this.levelUpdatedAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Obtiene el nivel siguiente al actual
     */
    public UserLevel getNextLevel() {
        return this.level.getNextLevel();
    }

    /**
     * Obtiene los puntos necesarios para el siguiente nivel
     */
    public int getPointsToNextLevel() {
        return this.level.getPointsToNextLevel(this.totalPoints);
    }

    /**
     * Obtiene el porcentaje de avance hacia el siguiente nivel
     */
    public double getProgressToNextLevel() {
        UserLevel next = this.level.getNextLevel();
        if (next == null) {
            return 100.0;  // Ya en nivel máximo
        }

        int currentLevelMin = this.level.getMinPoints();
        int nextLevelMin = next.getMinPoints();
        int pointsInCurrentLevel = this.totalPoints - currentLevelMin;
        int pointsNeededForNext = nextLevelMin - currentLevelMin;

        return (double) pointsInCurrentLevel / pointsNeededForNext * 100;
    }

    /**
     * Verifica si la suscripción premium está vigente
     */
    public boolean isActivePremium() {
        return premium && premiumUntil != null && premiumUntil.isAfter(LocalDateTime.now());
    }

    // ==============================================
    // MÉTODOS EXISTENTES
    // ==============================================

    // 🔥 MÉTODOS GETTERS MANUALES PARA COMPATIBILIDAD
    public boolean isActive() {
        return active;
    }

    public boolean getActive() {
        return active;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean getEmailVerified() {
        return emailVerified;
    }

    // Método que se ejecuta antes de persistir
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // Método que se ejecuta antes de actualizar
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    // Métodos útiles
    public void addPoints(int points) {
        this.totalPoints += points;
    }

    public void subtractPoints(int points) {
        if (this.totalPoints >= points) {
            this.totalPoints -= points;
        } else {
            throw new IllegalStateException("Puntos insuficientes");
        }
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    public boolean isSuspended() {
        return suspended != null && suspended;
    }

    public void suspend(String reason) {
        this.suspended = true;
        this.suspensionReason = reason;
        this.suspendedAt = LocalDateTime.now();
        this.active = false;  // Opcional: desactivar también
    }

    public void unsuspend() {
        this.suspended = false;
        this.suspensionReason = null;
        this.suspendedAt = null;
        this.active = true;
    }
}