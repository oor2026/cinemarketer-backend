package com.example.demo.domain.subscription;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_pending_confirmations")
@Data
@NoArgsConstructor
public class SubscriptionPendingConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "mp_payer_email", nullable = false)
    private String mpPayerEmail;

    @Column(name = "mp_preapproval_id", nullable = false)
    private String mpPreapprovalId;

    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_user_id")
    private Long confirmedUserId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        expiresAt = LocalDateTime.now().plusDays(7);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
