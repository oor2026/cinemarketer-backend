package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointHistoryResponse {

    private Integer totalPoints;        // Puntos disponibles actuales
    private Integer totalEarned;        // Total histórico acumulado (ganado)
    private Integer totalSpent;         // Total histórico canjeado (legacy)
    private Integer earnedThisMonth;    // Acumulados este mes (aún no liberados)

    // Nuevos campos — sistema de puntos
    private Integer accumulatedPoints;  // Puntos acumulados mes en curso
    private Integer redeemedThisMonth;  // Canjeados en el mes actual
    private Integer totalRedeemed;      // Canjeados histórico (base para insignias)

    private List<PointTransactionDto> transactions;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalElements;
}