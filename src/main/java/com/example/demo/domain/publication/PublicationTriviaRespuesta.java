package com.example.demo.domain.publication;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "publication_trivia_respuestas",
        uniqueConstraints = @UniqueConstraint(columnNames = {"publication_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationTriviaRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publication_id", nullable = false)
    private Long publicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opcion_id", nullable = false)
    private PublicationTriviaOpcion opcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}