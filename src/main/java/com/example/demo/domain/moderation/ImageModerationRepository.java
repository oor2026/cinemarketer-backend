package com.example.demo.domain.moderation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ImageModerationRepository extends JpaRepository<ImageModeration, Long> {
    Optional<ImageModeration> findByImageUrl(String imageUrl);
}