package com.example.demo.application.dtos;

import com.example.demo.domain.reward.RewardType;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AdminRewardRequest {
    private String name;
    private String description;
    private RewardType rewardType;
    private Integer pointsRequired;
    private Integer stock;
    private LocalDate expiryDate;
    private String termsConditions;
    private Boolean active = true;
    private String partner;
    private String website;
    private java.math.BigDecimal discountValue;
    private String discountType;
    private String experienceType;
    private String location;
    private java.time.LocalDateTime eventDate;
    private Integer maxCapacity;
    private java.time.LocalDateTime drawDate;
    // Merchandising
    private String brand;
    private String material;
    private String color;
    private String size;
    private String dimensions;
    private String weight;
    private String origin;
    private String unitsIncluded;
    private String condition;

    // Entrada de cine
    private String cinemaChain;
    private String cinemaFormat;
    private String cinemaRestrictions;
    private Integer ticketsIncluded;
    private Boolean includesSnack;

    // Descuento
    private String discountChannel;
    private java.math.BigDecimal minimumPurchase;
    private String applicableProducts;
    private Boolean stackable;

    // Experiencia
    private String duration;
    private Boolean includesTransport;
    private String requirements;
    private Boolean companionAllowed;
}
