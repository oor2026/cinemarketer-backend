package com.example.demo.application.dtos;

import com.example.demo.domain.reward.RewardType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RewardDto {

    private Long id;
    private String name;
    private String description;
    private RewardType rewardType;
    private Integer pointsRequired;
    private Integer stock;
    private String imageUrl;
    private LocalDate expiryDate;
    private String termsConditions;
    private Boolean active;
    private String partner;
    private String website;
    // Campos para DESCUENTO
    private java.math.BigDecimal discountValue;
    private String discountType;
    private String discountCode;
    private String discountChannel;
    private java.math.BigDecimal minimumPurchase;
    private String applicableProducts;
    private Boolean stackable;

    // Campos para EXPERIENCIA
    private String experienceType;
    private String location;
    private java.time.LocalDateTime eventDate;
    private Integer maxCapacity;
    private java.time.LocalDateTime drawDate;
    private Boolean drawExecuted;
    private String duration;
    private Boolean includesTransport;
    private String requirements;
    private Boolean companionAllowed;

    // Campos para MERCHANDISING
    private String brand;
    private String material;
    private String color;
    private String size;
    private String dimensions;
    private String weight;
    private String origin;
    private String unitsIncluded;
    private String condition;

    // Campos para TICKET (entrada de cine)
    private String cinemaChain;
    private String cinemaFormat;
    private String cinemaRestrictions;
    private Integer ticketsIncluded;
    private Boolean includesSnack;

    // Entrega (comunes)
    private String deliveryMethod;
    private String pickupPoint;
    private String deliveryCost;

    // Descuento extra
    private String redeemMethod;

    // Experiencia extra
    private Boolean requiresConfirmation;
    private Boolean transferable;
    private String organizer;

    // Campos calculados para el frontend
    private Boolean canRedeem;
    private Boolean isExpired;
    private Boolean hasStock;

    // Imágenes múltiples
    private java.util.List<ImageDto> images;

    @Data
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ImageDto {
        private Long id;
        private String imageUrl;
        private boolean primary;
    }
}
