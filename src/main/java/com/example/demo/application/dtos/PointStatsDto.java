package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointStatsDto {
    private long totalEarned;
    private long totalSpent;
    private double averagePerUser;
    private List<Map<String, Object>> topActions;
    private List<Map<String, Object>> distribucionPorAccion;
}