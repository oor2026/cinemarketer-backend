package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointHistoryResponse {

    private Integer totalPoints;     // Puntos actuales del usuario
    private Integer totalEarned;     // Total histórico ganado
    private Integer totalSpent;      // Total histórico gastado
    private Integer earnedThisMonth; // Ganados este mes

    private List<PointTransactionDto> transactions;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalElements;
}
