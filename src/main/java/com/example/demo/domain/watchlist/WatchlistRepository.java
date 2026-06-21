package com.example.demo.domain.watchlist;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Watchlist> findByUserIdAndMovieId(Long userId, Long movieId);

    boolean existsByUserIdAndMovieId(Long userId, Long movieId);

    Optional<Watchlist> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long count();  // ya existe en JpaRepository

    @Query("SELECT COUNT(DISTINCT w.movieId) FROM Watchlist w")
    long countDistinctMovies();

    @Query("SELECT COUNT(DISTINCT w.user.id) FROM Watchlist w")
    long countDistinctUsers();

    @Query("SELECT w.movieTitle, COUNT(w) as total FROM Watchlist w " +
            "WHERE w.movieTitle IS NOT NULL " +
            "GROUP BY w.movieId, w.movieTitle ORDER BY total DESC")
    List<Object[]> findTopMovies(Pageable pageable);

    @Query("SELECT w.movieGenres FROM Watchlist w WHERE w.movieGenres IS NOT NULL")
    List<String> findAllMovieGenres();
}
