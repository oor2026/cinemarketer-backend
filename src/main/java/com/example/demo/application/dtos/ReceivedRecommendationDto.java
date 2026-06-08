package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ReceivedRecommendationDto {
    private Long id;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private Long movieId;
    private String movieTitle;
    private String moviePosterPath;
    private String contextType;
    private String status;
    private LocalDateTime seenAt;
    private Short rating;
    private LocalDateTime createdAt;
}
