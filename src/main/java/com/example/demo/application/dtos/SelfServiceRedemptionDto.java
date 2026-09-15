package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelfServiceRedemptionDto {
    private Long id;
    private String redemptionType;          // "FREE" o "PREMIUM"
    private String rewardName;
    private String rewardImageUrl;
    private String status;                  // PENDING o COORDINATED
    private String deliveryMethod;          // RETIRO_PRESENCIAL, ENVIO_DOMICILIO, etc.

    // Solo si deliveryMethod = RETIRO_PRESENCIAL (u otro no-domicilio) y status = PENDING
    private List<DeliveryPointDto> deliveryPoints;

    // Solo si status = COORDINATED
    private DeliveryPointDto chosenDeliveryPoint;
    private String deliveryAddress;

    // Fijo, igual para todos — el WhatsApp único de soporte
    private String whatsappSupportPhone;
}
