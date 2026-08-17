package com.example.demo.domain.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SeriesWatchlistRepository extends JpaRepository<SeriesWatchlist, Long> {

    List<SeriesWatchlist> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SeriesWatchlist> findByUserIdAndSeriesId(Long userId, Long seriesId);

    boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

    Optional<SeriesWatchlist> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT w.user.id) FROM SeriesWatchlist w")
    long countDistinctUsers();

    @org.springframework.data.jpa.repository.Query("SELECT w.seriesTitle, COUNT(w) as total FROM SeriesWatchlist w " +
            "WHERE w.seriesTitle IS NOT NULL " +
            "GROUP BY w.seriesId, w.seriesTitle ORDER BY total DESC")
    java.util.List<Object[]> findTopSeries(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT w.seriesGenres FROM SeriesWatchlist w WHERE w.seriesGenres IS NOT NULL")
    java.util.List<String> findAllSeriesGenres();
}