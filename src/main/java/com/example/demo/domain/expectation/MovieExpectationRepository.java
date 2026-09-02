package com.example.demo.domain.expectation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieExpectationRepository extends JpaRepository<MovieExpectation, Long> {

    Optional<MovieExpectation> findByUserIdAndMovieId(Long userId, Long movieId);

    long countByMovieIdAndExpectingTrue(Long movieId);

    List<MovieExpectation> findByNotifyOnReleaseTrueAndNotifiedFalse();
}