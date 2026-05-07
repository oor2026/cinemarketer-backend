package com.example.demo.domain.premium;

import com.example.demo.domain.user.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "premium_rewards")
@NoArgsConstructor
@AllArgsConstructor
public class PremiumReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PremiumRewardType type;             // CANJEABLE o SORTEO

    @Column(name = "points_required")
    private int pointsRequired = 0;             // solo para CANJEABLE

    private Integer stock;                      // null = ilimitado para sorteos

    @Column(name = "draw_date")
    private LocalDateTime drawDate;             // solo para SORTEO

    @Column(name = "draw_executed")
    private boolean drawExecuted = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "winner_user_id")
    @JsonIgnoreProperties({"password", "verificationToken", "resetPasswordToken",
            "subscriptions", "redemptions", "reviews", "comments"})
    private User winner;                        // ganador del sorteo

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(length = 200)
    private String partner;                     // socio/proveedor

    @Column(length = 500)
    private String website;                     // sitio web del socio

    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean hasStock() {
        return stock == null || stock > 0;
    }

    public void decreaseStock() {
        if (stock != null && stock > 0) {
            stock--;
        }
    }
}
