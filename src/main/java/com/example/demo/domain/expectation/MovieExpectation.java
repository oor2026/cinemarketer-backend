package com.example.demo.domain.expectation;

import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "movie_expectations", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieExpectation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    // Relación opcional, mismo criterio que Review.movie — sin FK
    // estricta, porque movieId es el ID de TMDb, no siempre existe
    // una fila local en la tabla movies.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Movie movie;

    @Column(nullable = false)
    private Integer rating; // 1 a 5 estrellas

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "notify_on_release")
    private boolean notifyOnRelease = false;

    @Column(name = "notified")
    private boolean notified = false;

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