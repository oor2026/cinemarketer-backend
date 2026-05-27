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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    // Mensaje legible
    @Column(nullable = false)
    private String message;

    // Referencias para navegación
    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "movie_title")
    private String movieTitle;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "reply_id")
    private Long replyId;

    @Column(name = "read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
