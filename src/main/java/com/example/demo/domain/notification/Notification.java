package com.example.demo.domain.notification;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que RECIBE la notificacion
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Usuario que GENERÓ la accion (quien bancó, respondió, etc.)
    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    // Mensaje legible
    @Column(nullable = false)
    private String message;

    // Referencias para navegación
    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "movie_title")
    private String movieTitle;

    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "series_title")
    private String seriesTitle;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "reply_id")
    private Long replyId;

    @Column(name = "publication_id")
    private Long publicationId;

    @Column(name = "reward_id")
    private Long rewardId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
