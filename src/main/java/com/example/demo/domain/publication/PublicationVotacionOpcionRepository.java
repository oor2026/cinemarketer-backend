package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PublicationVotacionOpcionRepository extends JpaRepository<PublicationVotacionOpcion, Long> {
    List<PublicationVotacionOpcion> findByPublicationIdOrderByOrdenAsc(Long publicationId);
}