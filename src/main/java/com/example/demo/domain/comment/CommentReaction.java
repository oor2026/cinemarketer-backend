package com.example.demo.domain.comment;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment_reactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "reply_id", "user_id", "type"}))
@Data
@NoArgsConstructor
public class CommentReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_id", nullable = true)
    private CommentReply reply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReactionType type;

    // Si esta reaccion esta activa (visible)
    @Column(nullable = false)
    private boolean active = true;

    // Para MERECE_PUNTO: si ya se otorgaron puntos al autor
    // Una vez true, los puntos no se pueden reotorgar aunque se reactive
    @Column(name = "points_awarded", nullable = false)
    private boolean pointsAwarded = false;

    // Para MERECE_PUNTO: si el punto ya paso a disponible (no se puede retirar)
    @Column(name = "point_locked", nullable = false)
    private boolean pointLocked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
