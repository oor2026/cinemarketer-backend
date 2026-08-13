package com.example.demo.domain.series;

import com.example.demo.domain.user.User;
import com.example.demo.domain.review.VoteType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "series_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeriesReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = true, length = 10)
    private VoteType vote;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "points_awarded")
    private int pointsAwarded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Series series;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}