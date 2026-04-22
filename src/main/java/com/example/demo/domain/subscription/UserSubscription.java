package com.example.demo.domain.subscription;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_subscriptions")
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "mp_preapproval_id", length = 100)
    private String mpPreapprovalId;             // ID suscripción en MP

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;              // fin del período pagado

    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;      // próximo débito

    @Column(name = "last_payment_status", length = 50)
    private String lastPaymentStatus;           // approved, rejected, pending

    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE
                && endDate != null
                && endDate.isAfter(LocalDateTime.now());
    }
}
