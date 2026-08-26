package com.example.demo.domain.watchlist;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
@Data
@NoArgsConstructor
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "movie_title")
    private String movieTitle;

    @Column(name = "movie_poster_path")
    private String moviePosterPath;

    @Column(name = "movie_overview", columnDefinition = "TEXT")
    private String movieOverview;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "seen_at")
    private LocalDateTime seenAt;

    @Column(name = "rating")
    private Short rating;

    @Column(length = 50)
    private String motivo; // opcional — por qué la guardó (chip elegido en el modal post-guardado)

    @Column(name = "rated_at")
    private LocalDateTime ratedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "movie_genres", columnDefinition = "TEXT")
    private String movieGenres; // JSON array: ["Acción","Thriller"]

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
