package com.example.demo.domain.publication;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un intento de publicar/editar que el filtro de palabras prohibidas bloqueó.
 * Guarda SOLO la categoría (origen + campo), nunca el texto ni la palabra detectada:
 * así no se expone la lista del filtro ni se guarda contenido que ni siquiera se publicó.
 */
@Entity
@Table(name = "moderation_block_events",
        indexes = @Index(name = "idx_moderation_block_events_user", columnList = "user_id, created_at"))
@Data
@NoArgsConstructor
public class ModerationBlockEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** CREAR | EDITAR */
    @Column(name = "origin", nullable = false, length = 10)
    private String origin;

    /** TITULO | CONTENIDO | HASHTAGS | TRIVIA */
    @Column(name = "blocked_field", nullable = false, length = 15)
    private String blockedField;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
