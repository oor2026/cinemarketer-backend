package com.example.demo.domain.recommendation;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "series_recommendations")
@Data
@NoArgsConstructor
public class SeriesRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @Column(name = "series_title")
    private String seriesTitle;

    @Column(name = "series_poster_path")
    private String seriesPosterPath;

    @Column(name = "context_type", length = 100)
    private String contextType;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "seen_at")
    private LocalDateTime seenAt;

    @Column(name = "rating")
    private Short rating;

    @Column(name = "rated_at")
    private LocalDateTime ratedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Column(name = "series_overview", columnDefinition = "TEXT")
    private String seriesOverview;
}