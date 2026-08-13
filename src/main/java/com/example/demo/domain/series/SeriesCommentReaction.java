package com.example.demo.domain.series;

import com.example.demo.domain.comment.ReactionType;
import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "series_comment_reactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "reply_id", "user_id", "type"}))
@Data
@NoArgsConstructor
public class SeriesCommentReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private SeriesComment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_id", nullable = true)
    private SeriesCommentReply reply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReactionType type;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "points_awarded", nullable = false)
    private boolean pointsAwarded = false;

    @Column(name = "point_locked", nullable = false)
    private boolean pointLocked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}