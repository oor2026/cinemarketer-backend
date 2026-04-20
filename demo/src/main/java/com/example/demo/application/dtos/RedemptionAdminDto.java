package com.example.demo.application.dtos;

import com.example.demo.domain.redemption.RedemptionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionAdminDto {
    private Long id;
    private UserBasicDto user;
    private RewardBasicDto reward;
    private Integer pointsSpent;
    private LocalDateTime redemptionDate;
    private RedemptionStatus status;
    private String redemptionCode;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private boolean isExpired;
    private boolean isUsed;
}