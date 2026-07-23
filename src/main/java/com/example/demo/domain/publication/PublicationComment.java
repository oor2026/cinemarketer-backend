package com.example.demo.domain.publication;

import com.example.demo.domain.user.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "publication_comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publication_id", nullable = false)
    @JsonIgnoreProperties({"user", "imageUrls", "videoUrl", "hibernateLazyInitializer"})
    private Publication publication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"reviews", "redemptions", "sweepstakeEntries", "password",
            "verificationToken", "resetPasswordToken", "hibernateLazyInitializer"})
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean spoiler = false;

    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "report_count", nullable = false)
    private int reportCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 20, nullable = false)
    private PublicationCommentModerationStatus moderationStatus = PublicationCommentModerationStatus.APPROVED;

    @Column(name = "admin_reviewed", nullable = false)
    private boolean adminReviewed = false;

    @Column(name = "moderation_reviewed_at")
    private LocalDateTime moderationReviewedAt;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Transient
    private long replyCount;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}