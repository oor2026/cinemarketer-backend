package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PublicationTriviaRespuestaRepository extends JpaRepository<PublicationTriviaRespuesta, Long> {
    boolean existsByPublicationIdAndUserId(Long publicationId, Long userId);
    Optional<PublicationTriviaRespuesta> findByPublicationIdAndUserId(Long publicationId, Long userId);
    long countByOpcionId(Long opcionId);
}