package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PublicationTriviaOpcionRepository extends JpaRepository<PublicationTriviaOpcion, Long> {
    List<PublicationTriviaOpcion> findByPublicationIdOrderByOrdenAsc(Long publicationId);
}