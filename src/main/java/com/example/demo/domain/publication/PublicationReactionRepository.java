package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PublicationReactionRepository extends JpaRepository<PublicationReaction, Long> {

    Optional<PublicationReaction> findByPublicationIdAndUserIdAndReactionType(
            Long publicationId, Long userId, PublicationReactionType reactionType);

    long countByPublicationIdAndReactionTypeAndActiveTrue(
            Long publicationId, PublicationReactionType reactionType);

    boolean existsByPublicationIdAndUserIdAndReactionTypeAndActiveTrue(
            Long publicationId, Long userId, PublicationReactionType reactionType);

    List<PublicationReaction> findByPublicationIdAndReactionType(
            Long publicationId, PublicationReactionType reactionType);
}