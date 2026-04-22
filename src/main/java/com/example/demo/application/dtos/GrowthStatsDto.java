package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrowthStatsDto {
    private double userGrowth;
    private double voteGrowth;
    private double redemptionGrowth;
    private double churnRate;
    private double registrationToVoteRate;
    private double voteToCommentRate;
    private double voteToRedemptionRate;
    private double redemptionToSecondRate;
    private Map<String, Long> weekdayDistribution;
    private Map<String, Long> hourDistribution;
}
