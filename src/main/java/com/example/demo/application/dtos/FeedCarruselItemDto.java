package com.example.demo.application.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FeedCarruselItemDto {
    private Long id;
    private String tipo;
    private Long rewardId;
    private Integer orderIndex;
    private LocalDateTime addedAt;
    private String updatedByAdminEmail;
}