package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteStatsSectionDto {
    private long totalVotes;
    private long totalLikes;
    private long totalDislikes;
    private double approvalRate;
    private double growth;
    private List<Map<String, Object>> topContent; // películas o series según la sección
    private List<Map<String, Object>> topUsers;
    private Map<String, Long> dailyTrend;
}