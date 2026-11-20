package com.example.demo.domain.publication;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Un clic en "Compartir" sobre una publicación. Mide la intención de compartir, no que se haya compartido. */
@Entity
@Table(name = "publication_share_clicks",
        indexes = @Index(name = "idx_publication_share_clicks_pub", columnList = "publication_id, created_at"))
@Data
@NoArgsConstructor
public class PublicationShareClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publication_id", nullable = false)
    private Long publicationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
