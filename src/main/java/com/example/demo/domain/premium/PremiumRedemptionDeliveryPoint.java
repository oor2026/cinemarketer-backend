package com.example.demo.domain.premium;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "premium_redemption_delivery_points")
@NoArgsConstructor
@AllArgsConstructor
public class PremiumRedemptionDeliveryPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redemption_id", nullable = false)
    private PremiumRedemption redemption;

    @Column(name = "location_reference", nullable = false, length = 150)
    private String locationReference;

    @Column(name = "schedule_info", nullable = false, length = 300)
    private String scheduleInfo;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
