package com.example.demo.domain.publication;

import com.example.demo.domain.user.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "publications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"reviews", "redemptions", "sweepstakeEntries", "password",
            "verificationToken", "resetPasswordToken", "hibernateLazyInitializer"})
    private User user;

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "territory_group", nullable = false, length = 100)
    private String territoryGroup;

    @Column(name = "territory_sub", length = 100)
    private String territorySub;

    @Column(name = "tone", length = 50)
    private String tone;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "spoiler", nullable = false)
    private boolean spoiler = false;

    @Column(name = "image_urls", columnDefinition = "TEXT[]")
    private String[] imageUrls;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    // UID del video en Cloudflare Stream — distinto de videoUrl (la URL final
    // de reproducción). Se usa para consultar estado de encoding y armar frames.
    @Column(name = "video_uid", length = 255)
    private String videoUid;

    // Estado de moderación del video en particular, independiente de moderationStatus
    // (que es el de la publicación completa). Permite que un video agregado por
    // edición quede pendiente de revisión sin afectar el engagement ya generado
    // por una publicación que ya estaba APPROVED y viva.
    @Enumerated(EnumType.STRING)
    @Column(name = "video_moderation_status", length = 20)
    private PublicationModerationStatus videoModerationStatus;

    // Solo tiene valor cuando moderationStatus = PENDING_REVIEW. Le dice al
    // admin SI la revisión es por riesgo real detectado en la imagen, o por
    // el control obligatorio de cuenta nueva (primeras 3 publicaciones con
    // imagen/video) — sin esto, el admin ve "BAJO" en una pendiente y no
    // entiende por qué está ahí.
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_review_reason", length = 30)
    private MotivoRevisionPublicacion pendingReviewReason;

    // Los 5 frames extraídos para moderación — se guardan para que el panel
    // admin pueda mostrar el riesgo y las imágenes de referencia del video.
    @Column(name = "video_frame_urls", columnDefinition = "TEXT[]")
    private String[] videoFrameUrls;

    @Column(name = "hashtags", columnDefinition = "TEXT[]")
    private String[] hashtags;

    @Column(name = "report_count", nullable = false)
    private int reportCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 20, nullable = false)
    private PublicationModerationStatus moderationStatus = PublicationModerationStatus.APPROVED;

    @Column(name = "admin_reviewed", nullable = false)
    private boolean adminReviewed = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "points_awarded")
    private int pointsAwarded = 0;

    // Foto del estado del autor AL MOMENTO de publicar — no el estado actual.
    // Así la insignia "Autor" de Creator Tools no aparece/desaparece
    // retroactivamente si el usuario después cambia de plan.
    @Column(name = "author_was_creator", nullable = false)
    private boolean authorWasCreator = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (moderationStatus == null) moderationStatus = PublicationModerationStatus.APPROVED;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}