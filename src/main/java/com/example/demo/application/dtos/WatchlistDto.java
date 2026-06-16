package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistDto {

    private Long id;
    private Long movieId;
    private String movieTitle;
    private String moviePosterPath;
    private String movieOverview;
    private String status;
    private LocalDateTime seenAt;
    private Short rating;
    private LocalDateTime ratedAt;
    private LocalDateTime createdAt;
}
