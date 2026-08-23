package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponseDto {
    private StatsSummaryDto summary;
    private UserStatsDto users;
    private VoteStatsDto votes;
    private CommentStatsDto comments;
    private RedemptionStatsDto redemptions;
    private PointStatsDto points;
    private SupportStatsDto support;
    private GrowthStatsDto growth;
    private String period;
    private PremiumStatsDto premium;
    private SubscriptionStatsDto subscriptions;
    private RecommendationStatsDto recommendations;
    private WatchlistStatsDto watchlist;
    private RevenueStatsDto revenue;
    private PublicationStatsDto publications;
    private NoVistasStatsDto noVistas;
}