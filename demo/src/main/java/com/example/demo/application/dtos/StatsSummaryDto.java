package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsSummaryDto {
    private long totalUsers;
    private double approvalRate;
    private long totalRedemptions;
    private long openTickets;
    private double userGrowth;
    private double approvalGrowth;
    private double redemptionGrowth;
    private long openTicketsCount;
}