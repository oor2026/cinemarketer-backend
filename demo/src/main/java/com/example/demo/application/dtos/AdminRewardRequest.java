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
}
