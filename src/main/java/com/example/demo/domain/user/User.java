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

    @Column(nullable = false)
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

    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

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
        return this.level.getPointsToNextLevel(this.totalPoints);
    }

    public double getProgressToNextLevel() {
        UserLevel next = this.level.getNextLevel();
        if (next == null) {
            return 100.0;
        }
        int currentLevelMin = this.level.getMinPoints();
        int nextLevelMin = next.getMinPoints();
        int pointsInCurrentLevel = this.totalPoints - currentLevelMin;
        int pointsNeededForNext = nextLevelMin - currentLevelMin;
        return (double) pointsInCurrentLevel / pointsNeededForNext * 100;
    }

    public boolean isActivePremium() {
        return premium && premiumUntil != null && premiumUntil.isAfter(LocalDateTime.now());
    }
}