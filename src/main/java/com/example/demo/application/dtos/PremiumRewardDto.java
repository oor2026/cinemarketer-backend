package com.example.demo.application.dtos;

import com.example.demo.domain.premium.PremiumRewardType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PremiumRewardDto {

    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private PremiumRewardType type;         // CANJEABLE o SORTEO
    private int pointsRequired;             // solo para CANJEABLE
    private Integer stock;
    private LocalDateTime drawDate;         // solo para SORTEO
    private boolean drawExecuted;
    private String winnerName;              // nombre del ganador si ya se ejecutó
    private boolean active;

    // Calculados según el usuario autenticado
    private boolean canRedeem;              // tiene puntos suficientes
    private boolean alreadyEntered;        // ya se anotó en el sorteo
    private long totalEntries;             // cantidad de participantes en el sorteo
    private boolean userIsPremium;         // el usuario tiene suscripción activa

    private String partner;
    private String website;
    private String termsConditions;

    // Campos para SORTEO
    private String winner1Name;
    private Long   winner1Id;
    private String winner2Name;
    private Long   winner2Id;
    private String winner3Name;
    private Long   winner3Id;

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

    // Imágenes múltiples
    private java.util.List<ImageDto> images;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ImageDto {
        private Long id;
        private String imageUrl;
        private boolean primary;
    }
}
