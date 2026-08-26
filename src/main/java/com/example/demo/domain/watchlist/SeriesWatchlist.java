package com.example.demo.domain.watchlist;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "series_watchlist",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "series_id"}))
@Data
@NoArgsConstructor
public class SeriesWatchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @Column(name = "series_title")
    private String seriesTitle;

    @Column(name = "series_poster_path")
    private String seriesPosterPath;

    @Column(name = "series_overview", columnDefinition = "TEXT")
    private String seriesOverview;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "seen_at")
    private LocalDateTime seenAt;

    @Column(length = 50)
    private String motivo; // opcional — por qué la guardó (chip elegido en el modal post-guardado)

    @Column(nullable = false)
    private boolean hidden = false; // "ocultar" en vez de borrar — preserva el historial para siempre

    @Column(name = "rating")
    private Short rating;

    @Column(name = "rated_at")
    private LocalDateTime ratedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "series_genres", columnDefinition = "TEXT")
    private String seriesGenres; // JSON array: ["Drama","Fantasía"]

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}