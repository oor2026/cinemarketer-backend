package com.example.demo.domain.premium;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "premium_redemptions")
@NoArgsConstructor
@AllArgsConstructor
public class PremiumRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_id", nullable = false)
    private PremiumReward reward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "points_spent")
    private int pointsSpent = 0;

    @Column(name = "redemption_code", length = 100)
    private String redemptionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PremiumRedemptionStatus status = PremiumRedemptionStatus.PENDING;

    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private LocalDateTime redeemedAt;

    @PrePersist
    protected void onCreate() {
        redeemedAt = LocalDateTime.now();
    }
}
