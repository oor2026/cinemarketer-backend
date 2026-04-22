package com.example.demo.domain.genre;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {

    // Buscar género por nombre exacto
    Optional<Genre> findByNameIgnoreCase(String name);

    // Buscar géneros por nombre que contenga
    List<Genre> findByNameContainingIgnoreCase(String name);

    // Buscar por ID de TMDb
    Optional<Genre> findByTmdbGenreId(Integer tmdbGenreId);

    // Buscar géneros activos
    List<Genre> findByActiveTrue();

    // Buscar géneros que tienen al menos N películas
    @Query("SELECT g FROM Genre g WHERE SIZE(g.movies) >= :minMovies")
    List<Genre> findGenresWithAtLeastMovies(@Param("minMovies") int minMovies);

    // Buscar géneros más populares (con más películas)
    @Query("SELECT g FROM Genre g ORDER BY SIZE(g.movies) DESC")
    List<Genre> findMostPopularGenres();

    // Verificar si existe un género por nombre
    boolean existsByNameIgnoreCase(String name);
}