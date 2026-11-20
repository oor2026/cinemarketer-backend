package com.example.demo.domain.publication;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Visualizaciones de una publicación, agrupadas por espectador y día (hora argentina).
 * viewer_key: "u:<id>" si está logueado, "a:<uuid>" si es anónimo. source: FEED o PUBLIC.
 * Alcance = espectadores logueados distintos. Visualizaciones = suma de "views".
 * Se escribe SIEMPRE con el upsert de PublicationViewRepository (no con save()).
 */
@Entity
@Table(name = "publication_views",
        uniqueConstraints = @UniqueConstraint(name = "uk_publication_views",
                columnNames = {"publication_id", "source", "viewer_key", "view_day"}),
        indexes = @Index(name = "idx_publication_views_pub_day", columnList = "publication_id, view_day"))
@Data
@NoArgsConstructor
public class PublicationView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publication_id", nullable = false)
    private Long publicationId;

    @Column(name = "viewer_key", nullable = false, length = 64)
    private String viewerKey;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "view_day", nullable = false)
    private LocalDate viewDay;

    @Column(name = "views", nullable = false)
    private int views = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
