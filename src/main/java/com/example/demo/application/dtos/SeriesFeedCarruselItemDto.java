package com.example.demo.application.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SeriesFeedCarruselItemDto {
    private Long id;
    private String tipo;
    private Long rewardId;
    private Long seriesId;
    private Integer orderIndex;
    private LocalDateTime addedAt;
    private String updatedByAdminEmail;
}
