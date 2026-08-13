package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeriesStatsDto {
    private Long seriesId;
    private long likes;
    private long dislikes;
    private double positivePercentage;
    private long totalVotes;
    private boolean userVoted;
    private String userVoteType;
    private Integer pointsAwarded;
}