package com.example.demo.domain.series;

import com.example.demo.domain.genre.Genre;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "series")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Series {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tmdb_id", unique = true)
    private Long tmdbId;  // ID de The Movie Database (namespace de TV, distinto al de películas)

    @Column(nullable = false, length = 255)
    private String title;  // TMDb lo llama "name" para series — se mantiene "title" acá por consistencia con el resto del sitio

    @Column(length = 2000)
    private String overview;

    @Column(name = "poster_path", length = 500)
    private String posterPath;

    @Column(name = "backdrop_path", length = 500)
    private String backdropPath;

    @Column(name = "first_air_date")
    private LocalDate firstAirDate;  // Equivalente a releaseDate en Movie

    @Column(name = "last_air_date")
    private LocalDate lastAirDate;  // Propio de series: última fecha con episodios emitidos

    @Column(name = "number_of_seasons")
    private Integer numberOfSeasons;

    @Column(name = "number_of_episodes")
    private Integer numberOfEpisodes;

    @Column(name = "episode_run_time")
    private Integer episodeRunTime;  // Duración promedio por episodio, en minutos

    @Column(name = "in_production")
    private Boolean inProduction;  // Propio de series: si sigue produciendo temporadas nuevas

    @Column(name = "vote_average")
    private Double voteAverage;

    @Column(name = "vote_count")
    private Integer voteCount;

    @ManyToMany
    @JoinTable(
            name = "series_genre",
            joinColumns = @JoinColumn(name = "series_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<Genre> genres;

    @Column(name = "original_language", length = 10)
    private String originalLanguage;

    @Column(name = "popularity")
    private Double popularity;

    @Column(name = "video", length = 500)
    private String videoUrl;

    @Column(name = "status", length = 50)
    private String status;  // "Returning Series", "Ended", "Canceled", etc.

    @Column(name = "tagline", length = 500)
    private String tagline;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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