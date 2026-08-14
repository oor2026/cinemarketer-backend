package com.example.demo.application.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SeriesFeedDestacadoDto {
    private Long seriesId;
    private LocalDateTime updatedAt;
    private String updatedByAdminEmail;
}
