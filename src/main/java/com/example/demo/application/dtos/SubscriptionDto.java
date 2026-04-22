package com.example.demo.application.dtos;

import com.example.demo.domain.subscription.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDto {

    // Datos del plan
    private Long planId;
    private String planName;
    private String planType;
    private BigDecimal planPrice;
    private int pointsMultiplier;
    private List<String> benefits;

    // Datos de la suscripción del usuario
    private Long subscriptionId;
    private SubscriptionStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime nextBillingDate;
    private String lastPaymentStatus;
    private LocalDateTime lastPaymentDate;
    private boolean active;

    // Respuesta al crear suscripción en MP
    private String initPoint;       // URL de pago (si se usa redirect)
    private String mpPreapprovalId; // ID de la suscripción en MP
}
