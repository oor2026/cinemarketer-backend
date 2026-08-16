package com.example.demo.domain.trivia;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trivia_series_attempts",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "fecha"}),
                @UniqueConstraint(columnNames = {"guest_token", "fecha"})
        })
@Data
public class TriviaSeriesAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "guest_token", length = 100)
    private String guestToken;

    @Column(name = "ip_invitado", length = 45)
    private String ipInvitado;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "preguntas_json", columnDefinition = "TEXT", nullable = false)
    private String preguntasJson;

    @Column(name = "pregunta_actual", nullable = false)
    private int preguntaActual = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriviaEstado estado = TriviaEstado.EN_CURSO;

    @Column(name = "puntos_ganados", nullable = false)
    private int puntosGanados = 0;

    @Version
    private Long version;

    @Column(name = "aciertos_json", columnDefinition = "TEXT")
    private String aciertosJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
