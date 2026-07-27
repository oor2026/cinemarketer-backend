package com.example.demo.domain.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedCarruselItemRepository extends JpaRepository<FeedCarruselItem, Long> {
    List<FeedCarruselItem> findAllByOrderByOrderIndexAsc();
    boolean existsByTipo(FeedCarruselTipo tipo);
    boolean existsByTipoAndRewardId(FeedCarruselTipo tipo, Long rewardId);
}