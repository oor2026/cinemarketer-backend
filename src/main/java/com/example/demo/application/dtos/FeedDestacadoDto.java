package com.example.demo.application.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FeedDestacadoDto {
    private Long movieId;
    private LocalDateTime updatedAt;
    private String updatedByAdminEmail;
}