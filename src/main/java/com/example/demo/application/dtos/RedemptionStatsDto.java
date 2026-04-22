package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionStatsDto {
    private long totalRewards;
    private long activeRewards;
    private long exhaustedRewards;
    private long totalRedemptions;
    private long pendingRedemptions;
    private long completedRedemptions;
    private long totalPointsSpent;
    private double redemptionRate;
    private double growth;
    private List<Map<String, Object>> topRewards;
}