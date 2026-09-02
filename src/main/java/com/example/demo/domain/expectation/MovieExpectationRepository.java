package com.example.demo.domain.expectation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MovieExpectationRepository extends JpaRepository<MovieExpectation, Long> {

    Optional<MovieExpectation> findByUserIdAndMovieId(Long userId, Long movieId);

    @Query("SELECT AVG(e.rating) FROM MovieExpectation e WHERE e.movieId = :movieId")
    Double findAverageByMovieId(@Param("movieId") Long movieId);

    long countByMovieId(Long movieId);
}