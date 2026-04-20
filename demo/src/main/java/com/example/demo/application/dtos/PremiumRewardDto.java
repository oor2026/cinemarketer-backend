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
}
