package com.example.demo.domain.redemption;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "redemption_delivery_points")
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionDeliveryPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redemption_id", nullable = false)
    private Redemption redemption;

    // Referencia aproximada — nunca la dirección exacta (esa se da por
    // WhatsApp una vez que el usuario ya eligió este punto).
    @Column(name = "location_reference", nullable = false, length = 150)
    private String locationReference;

    // Texto libre a propósito — ver justificación en la conversación de
    // diseño: no hay necesidad de que el sistema "entienda" el horario,
    // el WhatsApp cierra el detalle fino igual.
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