package com.example.demo.domain.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
public interface PublicationViewRepository extends JpaRepository<PublicationView, Long> {

    // Upsert atómico: si ya hay fila de ese espectador en ese día, suma 1 (con tope de 50 por
    // día y espectador, para que recargar la página mil veces no infle el número).
    @Modifying
    @Query(value = "INSERT INTO publication_views (publication_id, viewer_key, source, view_day, views, created_at) " +
            "VALUES (:publicationId, :viewerKey, :source, :viewDay, 1, :now) " +
            "ON CONFLICT (publication_id, source, viewer_key, view_day) " +
            "DO UPDATE SET views = LEAST(publication_views.views + 1, 50)",
            nativeQuery = true)
    void registrar(@Param("publicationId") Long publicationId,
                   @Param("viewerKey") String viewerKey,
                   @Param("source") String source,
                   @Param("viewDay") LocalDate viewDay,
                   @Param("now") LocalDateTime now);
}
