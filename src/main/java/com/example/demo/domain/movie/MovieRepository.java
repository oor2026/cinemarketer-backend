package com.example.demo.domain.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    // Buscar por título exacto (ignorando mayúsculas)
    Optional<Movie> findByTitleIgnoreCase(String title);

    // Buscar por título que contenga (búsqueda parcial)
    List<Movie> findByTitleContainingIgnoreCase(String title);

    // Buscar por ID de TMDb
    Optional<Movie> findByTmdbId(Long tmdbId);

    // Buscar películas en cartelera (fecha de estreno <= hoy)
    List<Movie> findByReleaseDateLessThanEqualOrderByReleaseDateDesc(LocalDate date);

    // Buscar próximos estrenos (fecha de estreno > hoy)
    List<Movie> findByReleaseDateGreaterThanOrderByReleaseDateAsc(LocalDate date);

    // Buscar por género
    @Query("SELECT m FROM Movie m JOIN m.genres g WHERE LOWER(g) = LOWER(:genre)")
    List<Movie> findByGenre(@Param("genre") String genre);

    // Buscar películas populares (por votos)
    List<Movie> findByVoteCountGreaterThanOrderByVoteAverageDesc(Integer minVotes);

    // Buscar películas por año
    List<Movie> findByReleaseDateYear(Integer year);

    @Query("SELECT m FROM Movie m WHERE YEAR(m.releaseDate) = :year")
    List<Movie> findByYear(@Param("year") int year);

    // Búsqueda avanzada por múltiples criterios
    @Query("SELECT m FROM Movie m WHERE " +
            "LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(m.overview) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Movie> searchByKeyword(@Param("keyword") String keyword);

    // Películas más votadas
    List<Movie> findTop10ByOrderByVoteAverageDesc();

    // Películas más populares (según TMDb)
    List<Movie> findTop20ByOrderByPopularityDesc();

    // Verificar si una película ya existe por tmdbId
    boolean existsByTmdbId(Long tmdbId);

    // Trae varias películas de una sola vez por su tmdbId — reemplaza
    // el patrón de hacer un findByTmdbId() por cada fila en un loop.
    List<Movie> findByTmdbIdIn(List<Long> tmdbIds);
}