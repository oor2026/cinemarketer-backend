package com.example.demo.domain.series;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeriesSpoilerAcceptedRepository extends JpaRepository<SeriesSpoilerAccepted, SeriesSpoilerAcceptedId> {

    boolean existsByIdUserIdAndIdSeriesId(Long userId, Long seriesId);
}