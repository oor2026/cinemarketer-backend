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

    // Campos calculados para el frontend
    private Boolean canRedeem;      // Si el usuario puede canjearlo (tiene puntos suficientes y hay stock)
    private Boolean isExpired;
    private Boolean hasStock;
}
