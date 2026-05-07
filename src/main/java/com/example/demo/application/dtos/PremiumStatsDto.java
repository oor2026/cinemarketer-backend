package com.example.demo.application.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PremiumStatsDto {
    private long totalPremiumRewards;
    private long activePremiumRewards;
    private long totalSorteos;
    private long sorteosEjecutados;
    private long sorteosPendientes;
    private long totalCanjeables;
    private long canjeablesActivos;
}