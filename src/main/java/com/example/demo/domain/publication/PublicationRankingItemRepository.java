package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PublicationRankingItemRepository extends JpaRepository<PublicationRankingItem, Long> {
    List<PublicationRankingItem> findByPublicationIdOrderByOrdenAsc(Long publicationId);
}