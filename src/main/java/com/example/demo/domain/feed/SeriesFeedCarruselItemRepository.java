package com.example.demo.domain.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeriesFeedCarruselItemRepository extends JpaRepository<SeriesFeedCarruselItem, Long> {

    List<SeriesFeedCarruselItem> findAllByOrderByOrderIndexAsc();

    boolean existsByTipo(SeriesFeedCarruselTipo tipo);

    boolean existsByTipoAndSeriesId(SeriesFeedCarruselTipo tipo, Long seriesId);

    boolean existsByTipoAndRewardId(SeriesFeedCarruselTipo tipo, Long rewardId);
}
