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
}
