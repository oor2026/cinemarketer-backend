package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PublicationCommentReactionRepository extends JpaRepository<PublicationCommentReaction, Long> {

    Optional<PublicationCommentReaction> findByCommentIdAndUserId(Long commentId, Long userId);

    long countByCommentIdAndActiveTrue(Long commentId);
}