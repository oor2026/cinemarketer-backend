package com.example.demo.application.dtos;

import com.example.demo.domain.redemption.RedemptionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionDto {

    private Long id;
    private Long rewardId;
    private String rewardName;
    private String rewardDescription;
    private String rewardImageUrl;
    private Integer pointsSpent;
    private RedemptionStatus status;
    private String redemptionCode;
    private LocalDateTime redemptionDate;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
