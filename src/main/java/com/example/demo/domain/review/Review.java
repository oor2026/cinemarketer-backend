package com.example.demo.domain.review;

import com.example.demo.domain.user.User;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.cinema.Cinema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")  // 👈 SIN uniqueConstraints
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 20)
    private ReviewType reviewType;  // MOVIE o CINEMA

    @Column(name = "target_id", nullable = false)
    private Long targetId;  // ID de la película o cine

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = true, length = 10)
    private VoteType vote;  // LIKE o DISLIKE (puede ser null)

    @Column(columnDefinition = "TEXT")
    private String comment;  // Para reseñas con texto

    @Column(name = "points_awarded")
    private int pointsAwarded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean active = true;

    // Relaciones opcionales para facilitar consultas
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Cinema cinema;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getContentType() {
        return reviewType == ReviewType.MOVIE ? "película" : "cine";
    }
}
