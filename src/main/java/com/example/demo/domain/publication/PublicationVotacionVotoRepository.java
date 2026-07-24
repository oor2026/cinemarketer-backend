package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PublicationVotacionVotoRepository extends JpaRepository<PublicationVotacionVoto, Long> {
    boolean existsByPublicationIdAndUserId(Long publicationId, Long userId);
    Optional<PublicationVotacionVoto> findByPublicationIdAndUserId(Long publicationId, Long userId);
    long countByOpcionId(Long opcionId);
}