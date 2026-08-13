package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SeriesWatchlistDto {
    private Long id;
    private Long seriesId;
    private String seriesTitle;
    private String seriesPosterPath;
    private String seriesOverview;
    private String status;
    private LocalDateTime seenAt;
    private Short rating;
    private LocalDateTime ratedAt;
    private LocalDateTime createdAt;
}