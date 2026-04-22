package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteStatsDto {
    private long totalVotes;
    private long totalLikes;
    private long totalDislikes;
    private double approvalRate;
    private double growth;
    private List<Map<String, Object>> topMovies;
    private List<Map<String, Object>> topUsers;
    private Map<String, Long> dailyTrend;
}