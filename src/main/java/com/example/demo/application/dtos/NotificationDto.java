package com.example.demo.application.dtos;

import com.example.demo.domain.notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private String message;
    private Long actorId;
    private Long movieId;
    private String movieTitle;
    private Long seriesId;
    private String seriesTitle;
    private Long commentId;
    private Long replyId;
    private String referenceType;
    private boolean read;
    private LocalDateTime createdAt;
    private Long publicationId;
    private Long rewardId;
}