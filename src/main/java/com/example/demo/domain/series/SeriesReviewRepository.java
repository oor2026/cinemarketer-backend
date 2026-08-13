package com.example.demo.domain.series;

import com.example.demo.domain.review.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeriesReviewRepository extends JpaRepository<SeriesReview, Long> {

    long countBySeriesIdAndVote(Long seriesId, VoteType vote);

    Optional<SeriesReview> findByUserIdAndSeriesId(Long userId, Long seriesId);

    boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

    List<SeriesReview> findByUserIdAndVoteIsNotNullOrderByCreatedAtDesc(Long userId);
}