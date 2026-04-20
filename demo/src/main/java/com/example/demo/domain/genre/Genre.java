package com.example.demo.domain.genre;

import com.example.demo.domain.movie.Movie;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "genres")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;  // Ej: "Acción", "Comedia", "Drama"

    @Column(name = "tmdb_genre_id", unique = true)
    private Integer tmdbGenreId;  // ID del género en TMDb (opcional)

    @Column(length = 500)
    private String description;  // Descripción del género (opcional)

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relación inversa con Movie (opcional, no necesaria siempre)
    @ManyToMany(mappedBy = "genres")
    private List<Movie> movies;

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