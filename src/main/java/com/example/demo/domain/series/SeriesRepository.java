package com.example.demo.domain.series;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeriesRepository extends JpaRepository<Series, Long> {

    Optional<Series> findByTitleIgnoreCase(String title);

    List<Series> findByTitleContainingIgnoreCase(String title);

    Optional<Series> findByTmdbId(Long tmdbId);

    // Equivalente a "en cartelera": series que ya empezaron a emitirse
    List<Series> findByFirstAirDateLessThanEqualOrderByFirstAirDateDesc(LocalDate date);

    // Equivalente a "próximos estrenos"
    List<Series> findByFirstAirDateGreaterThanOrderByFirstAirDateAsc(LocalDate date);

    @Query("SELECT s FROM Series s JOIN s.genres g WHERE LOWER(g) = LOWER(:genre)")
    List<Series> findByGenre(@Param("genre") String genre);

    List<Series> findByVoteCountGreaterThanOrderByVoteAverageDesc(Integer minVotes);

    @Query("SELECT s FROM Series s WHERE YEAR(s.firstAirDate) = :year")
    List<Series> findByYear(@Param("year") int year);

    @Query("SELECT s FROM Series s WHERE " +
            "LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.overview) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Series> searchByKeyword(@Param("keyword") String keyword);

    List<Series> findTop10ByOrderByVoteAverageDesc();

    List<Series> findTop20ByOrderByPopularityDesc();

    boolean existsByTmdbId(Long tmdbId);

    // Mismo criterio que MovieRepository.findByTmdbIdIn — una sola
    // query para varias series en vez de una por cada una.
    List<Series> findByTmdbIdIn(List<Long> tmdbIds);
}