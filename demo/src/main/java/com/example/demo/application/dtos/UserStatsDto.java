package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDto {
    private long totalUsers;
    private long activeUsers;
    private long suspendedUsers;
    private long verifiedUsers;
    private long newUsers;
    private long usersWithPoints;
    private long inactiveUsers;
    private double growth;
    private long newUsersPrevPeriod;
}