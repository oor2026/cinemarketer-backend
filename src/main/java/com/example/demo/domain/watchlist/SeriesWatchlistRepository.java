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

    Optional<SeriesWatchlist> findByUserIdAndSeriesIdAndHiddenFalse(Long userId, Long seriesId);

    List<SeriesWatchlist> findByUserIdAndHiddenFalseOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndSeriesIdAndHiddenFalse(Long userId, Long seriesId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT w.user.id) FROM SeriesWatchlist w")
    long countDistinctUsers();

    @org.springframework.data.jpa.repository.Query("SELECT w.seriesTitle, COUNT(w) as total FROM SeriesWatchlist w " +
            "WHERE w.seriesTitle IS NOT NULL " +
            "GROUP BY w.seriesId, w.seriesTitle ORDER BY total DESC")
    java.util.List<Object[]> findTopSeries(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT w.seriesGenres FROM SeriesWatchlist w WHERE w.seriesGenres IS NOT NULL")
    java.util.List<String> findAllSeriesGenres();

    @org.springframework.data.jpa.repository.Query("SELECT w.motivo as motivo, COUNT(w) as total FROM SeriesWatchlist w " +
            "WHERE w.motivo IS NOT NULL " +
            "GROUP BY w.motivo ORDER BY total DESC")
    java.util.List<java.util.Map<String, Object>> findDistribucionMotivos();

    long countByMotivoIsNotNull();
}