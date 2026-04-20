package com.example.demo.domain.movie;

import com.example.demo.domain.genre.Genre;
import com.example.demo.domain.review.Review;
import com.example.demo.domain.sweepstake.Sweepstake;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "movies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tmdb_id", unique = true)
    private Long tmdbId;  // ID de The Movie Database

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 2000)
    private String overview;  // Sinopsis

    @Column(name = "poster_path", length = 500)
    private String posterPath;  // Ruta del póster

    @Column(name = "backdrop_path", length = 500)
    private String backdropPath;  // Imagen de fondo

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "runtime")
    private Integer runtime;  // Duración en minutos

    @Column(name = "vote_average")
    private Double voteAverage;  // Puntuación promedio de TMDb

    @Column(name = "vote_count")
    private Integer voteCount;  // Cantidad de votos en TMDb

    // En Movie.java - RELACIÓN CORREGIDA
    @ManyToMany
    @JoinTable(
            name = "movie_genre",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<Genre> genres;  // Ahora es una lista de objetos Genre

    @Column(name = "original_language", length = 10)
    private String originalLanguage;

    @Column(name = "popularity")
    private Double popularity;

    @Column(name = "video", length = 500)
    private String videoUrl;  // URL del trailer

    @Column(name = "imdb_id", length = 20)
    private String imdbId;

    @Column(name = "status", length = 50)
    private String status;  // "Released", "In Production", etc.

    @Column(name = "tagline", length = 500)
    private String tagline;

    @Column(name = "budget")
    private Long budget;

    @Column(name = "revenue")
    private Long revenue;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;  // Última sincronización con TMDb

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "movie")
    private List<Review> reviews;

    @OneToMany(mappedBy = "targetMovie")
    private List<Sweepstake> sweepstakes = new ArrayList<>();

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