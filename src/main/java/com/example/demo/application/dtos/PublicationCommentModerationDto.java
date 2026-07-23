package com.example.demo.application.dtos;

import com.example.demo.domain.publication.PublicationCommentModerationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationCommentModerationDto {

    private Long commentId;
    private String content;
    private LocalDateTime createdAt;
    private PublicationCommentModerationStatus moderationStatus;
    private Integer reportCount;
    private LocalDateTime moderationReviewedAt;

    // Dato de la publicación (para el label "Publicación ID: X" en el admin)
    private Long publicationId;

    // Datos del autor
    private Long authorId;
    private String authorName;
    private String authorEmail;

    // Reportes recibidos
    private List<ReportDetail> reports;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportDetail {
        private Long reportId;
        private Long reporterId;
        private String reporterName;
        private String reporterEmail;
        private String reason;
        private String description;
        private LocalDateTime createdAt;
    }
}