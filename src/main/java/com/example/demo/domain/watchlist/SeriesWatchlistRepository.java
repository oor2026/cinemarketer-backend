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
}