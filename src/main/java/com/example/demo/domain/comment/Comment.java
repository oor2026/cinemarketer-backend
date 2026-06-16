package com.example.demo.domain.comment;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(length = 2000, nullable = false)
    private String content;

    @Column(name = "points_awarded")
    private Integer pointsAwarded = 25;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Moderación ────────────────────────────────
    @Column(name = "report_count")
    private Integer reportCount = 0;

    @Column(name = "toxicity_score")
    private Float toxicityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 20)
    private ModerationStatus moderationStatus = ModerationStatus.APPROVED;

    @Column(name = "moderation_reviewed_at")
    private LocalDateTime moderationReviewedAt;

    @Column(name = "admin_reviewed", nullable = false)
    private boolean adminReviewed = false;

    @Column(name = "has_gif")
    private Boolean hasGif = false;

    @Column(name = "gif_url", length = 500)
    private String gifUrl;

    @Column(name = "spoiler", nullable = false)
    private boolean spoiler = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (moderationStatus == null) moderationStatus = ModerationStatus.APPROVED;
        if (reportCount == null) reportCount = 0;
    }
}