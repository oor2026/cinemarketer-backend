package com.example.demo.domain.review;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "voto_relampago_omitidas",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
@Data
@NoArgsConstructor
public class VotoRelampagoOmitida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // true cuando el usuario terminó votando (like/dislike) esta película
    // en cualquier parte de la app después de haberla omitido — deja de
    // contar para el cooldown de 20 días, pero el registro se conserva
    // para saber cuánta gente pasa de "no la vi" a votarla.
    @Column(name = "superseded_by_vote", nullable = false)
    private boolean supersededByVote = false;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}