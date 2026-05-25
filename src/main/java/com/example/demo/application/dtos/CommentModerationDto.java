package com.example.demo.application.dtos;

import com.example.demo.domain.comment.ModerationStatus;
import com.example.demo.domain.comment.ReportReason;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentModerationDto {

    private Long commentId;
    private String content;
    private LocalDateTime createdAt;
    private ModerationStatus moderationStatus;
    private Float toxicityScore;
    private Integer reportCount;
    private LocalDateTime moderationReviewedAt;

    // Datos de la película
    private Long movieId;

    // Datos del autor del comentario
    private Long authorId;
    private String authorName;
    private String authorEmail;

    // Reportes recibidos
    private List<ReportDetail> reports;

    // Para diferenciar comentarios de respuestas en el admin
    private Boolean isReply;
    private Long replyId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportDetail {
        private Long reportId;
        private Long reporterId;
        private String reporterName;
        private String reporterEmail;
        private ReportReason reason;
        private String description;
        private LocalDateTime createdAt;
    }
}
