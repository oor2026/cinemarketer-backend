package com.example.demo.domain.subscription;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "subscription_plans")
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;                        // "Premium"

    @Column(nullable = false, length = 50)
    private String type;                        // "MENSUAL"

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;                   // precio en ARS

    @Column(name = "points_multiplier", nullable = false)
    private int pointsMultiplier = 2;           // multiplicador x2

    @Column(name = "mp_preapproval_plan_id", length = 100)
    private String mpPreapprovalPlanId;         // ID del plan en MP

    @Column(columnDefinition = "TEXT")
    private String benefits;                    // JSON con lista de beneficios

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
