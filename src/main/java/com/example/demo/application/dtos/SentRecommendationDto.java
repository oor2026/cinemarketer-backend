package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SentRecommendationDto {
    private Long id;
    private Long receiverId;
    private String receiverName;
    private String receiverAvatarUrl;
    private Long movieId;
    private String movieTitle;
    private String moviePosterPath;
    private String movieOverview;
    private String contextType;
    private String status;
    private LocalDateTime seenAt;
    private Short rating;
    private LocalDateTime createdAt;
}