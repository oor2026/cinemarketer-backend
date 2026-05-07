package com.example.demo.domain.redemption;

import com.example.demo.domain.user.User;
import com.example.demo.domain.reward.Reward;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "redemptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_id", nullable = false)
    private Reward reward;

    @Column(name = "points_spent", nullable = false)
    private Integer pointsSpent;  // Puntos gastados en este canje

    @Column(name = "redemption_date", nullable = false)
    private LocalDateTime redemptionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RedemptionStatus status = RedemptionStatus.PENDING;

    @Column(name = "redemption_code", length = 100)
    private String redemptionCode;  // Código generado para el premio

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;  // Fecha de expiración del premio canjeado

    @Column(name = "used_at")
    private LocalDateTime usedAt;  // Fecha en que se usó el premio

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (redemptionDate == null) {
            redemptionDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Métodos útiles
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markAsUsed() {
        this.usedAt = LocalDateTime.now();
        this.status = RedemptionStatus.COMPLETED;
    }

    public void cancel() {
        this.status = RedemptionStatus.CANCELLED;
        // Devolver stock al premio
        this.reward.increaseStock();
    }
}