package com.example.demo.domain.user;

import com.example.demo.domain.redemption.Redemption;
import com.example.demo.domain.review.Review;
import com.example.demo.domain.sweepstake.SweepstakeEntry;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    @Column(name = "reset_password_token", length = 255)
    private String resetPasswordToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "available_points", nullable = false)
    private int availablePoints = 0;

    @Column(name = "accumulated_points", nullable = false)
    private int accumulatedPoints = 0;

    @Column(name = "total_redeemed_points", nullable = false)
    private int totalRedeemedPoints = 0;

    @Column(name = "free_monthly_cap")
    private Integer freeMonthlyCapOverride;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "dni", unique = true, length = 20)
    private String dni;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "google_id", unique = true, length = 255)
    private String googleId;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete = true;

    private boolean active = true;

    @Column(nullable = false)
    private Boolean suspended = false;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @OneToMany(mappedBy = "user")
    private List<Review> reviews;

    @OneToMany(mappedBy = "user")
    private List<Redemption> redemptions;

    @OneToMany(mappedBy = "user")
    private List<SweepstakeEntry> sweepstakeEntries;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_level", nullable = false)
    private UserLevel level = UserLevel.AMATEUR;

    @Column(name = "level_updated_at")
    private LocalDateTime levelUpdatedAt;

    @Column(name = "is_premium", nullable = false)
    private boolean premium = false;

    @Column(name = "premium_until")
    private LocalDateTime premiumUntil;

    // ==============================================
    // MÉTODOS DE NEGOCIO (NO GETTERS/SETTERS)
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

    /**
     * Suma puntos acumulados del mes en curso (no disponibles aún)
     */
    public void addAccumulatedPoints(int points) {
        this.accumulatedPoints += points;
    }

    /**
     * Descuenta puntos disponibles al momento del canje (FIFO se maneja en PointBatchService)
     * También incrementa el contador histórico de canjeados (base para insignias)
     */
    public void redeemPoints(int points) {
        if (this.availablePoints >= points) {
            this.availablePoints -= points;
            this.totalRedeemedPoints += points;
        } else {
            throw new IllegalStateException("Puntos disponibles insuficientes");
        }
    }

    /**
     * Suma puntos disponibles (llamado por el scheduler mensual al liberar lotes)
     */
    public void addAvailablePoints(int points) {
        this.availablePoints += points;
    }

    /**
     * Resta puntos acumulados (llamado por el scheduler al liberar — mueve acumulados a disponibles)
     */
    public void clearAccumulatedPoints(int pointsReleased) {
        this.accumulatedPoints = Math.max(0, this.accumulatedPoints - pointsReleased);
    }

    /**
     * @deprecated Usar addAccumulatedPoints() o redeemPoints() según el contexto
     */
    @Deprecated
    public void addPoints(int points) {
        this.accumulatedPoints += points;
    }

    /**
     * @deprecated Usar redeemPoints() para canjes
     */
    @Deprecated
    public void subtractPoints(int points) {
        if (this.availablePoints >= points) {
            this.availablePoints -= points;
            this.totalRedeemedPoints += points;
        } else {
            throw new IllegalStateException("Puntos disponibles insuficientes");
        }
    }

    /**
     * Compatibilidad temporal — devuelve puntos disponibles
     * @deprecated Usar getAvailablePoints()
     */
    @Deprecated
    public int getTotalPoints() {
        return this.availablePoints;
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
        this.active = false;
    }

    public void unsuspend() {
        this.suspended = false;
        this.suspensionReason = null;
        this.suspendedAt = null;
        this.active = true;
    }

    public void updateAvatar(String newAvatarUrl) {
        this.avatarUrl = newAvatarUrl;
        this.updatedAt = LocalDateTime.now();
    }

    public void removeAvatar() {
        this.avatarUrl = null;
        this.updatedAt = LocalDateTime.now();
    }

    public String getEffectiveAvatarUrl() {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            return avatarUrl;
        }
        return profileImageUrl;
    }

    /**
     * Actualiza el nivel basándose en los puntos históricos canjeados.
     * El nivel es una insignia acumulativa — nunca baja.
     */
    public boolean updateLevelBasedOnPoints() {
        UserLevel newLevel = UserLevel.getLevelByPoints(this.totalRedeemedPoints);
        // El nivel solo sube, nunca baja
        if (newLevel.ordinal() > this.level.ordinal()) {
            this.level = newLevel;
            this.levelUpdatedAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }

    public void setLevelManually(UserLevel newLevel) {
        if (newLevel != null) {
            this.level = newLevel;
            this.levelUpdatedAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
        }
    }

    public UserLevel getNextLevel() {
        return this.level.getNextLevel();
    }

    public int getPointsToNextLevel() {
        return this.level.getPointsToNextLevel(this.totalRedeemedPoints);
    }

    public double getProgressToNextLevel() {
        UserLevel next = this.level.getNextLevel();
        if (next == null) {
            return 100.0;
        }
        int currentLevelMin = this.level.getMinPoints();
        int nextLevelMin = next.getMinPoints();
        int pointsInCurrentLevel = this.totalRedeemedPoints - currentLevelMin;
        int pointsNeededForNext = nextLevelMin - currentLevelMin;
        return (double) pointsInCurrentLevel / pointsNeededForNext * 100;
    }

    /**
     * Retorna el tope mensual de liberación.
     * FREE: 20.000 pts (o valor personalizado). PREMIUM: sin tope (null).
     */
    public Integer getEffectiveMonthlyCap() {
        if (this.premium) return null;
        return freeMonthlyCapOverride != null ? freeMonthlyCapOverride : 20000;
    }

    public boolean isActivePremium() {
        return premium && premiumUntil != null && premiumUntil.isAfter(LocalDateTime.now());
    }
}