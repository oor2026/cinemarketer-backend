package com.example.demo.domain.trivia;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trivia_attempts",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "fecha"}),
                @UniqueConstraint(columnNames = {"guest_token", "fecha"})
        })
@Data
public class TriviaAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable — un intento es de un User O de un invitado (guestToken),
    // nunca ninguno de los dos ni los dos a la vez. Se valida en el service.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "guest_token", length = 100)
    private String guestToken;

    @Column(nullable = false)
    private LocalDate fecha;

    // Las 10 preguntas del día, ya resueltas y congeladas al crear el intento
    // (tipo, entidad, opciones y distractores) — así reanudar en otro
    // dispositivo muestra exactamente lo mismo, sin volver a sortear nada.
    @Column(name = "preguntas_json", columnDefinition = "TEXT", nullable = false)
    private String preguntasJson;

    @Column(name = "pregunta_actual", nullable = false)
    private int preguntaActual = 0; // índice 0-9

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriviaEstado estado = TriviaEstado.EN_CURSO;

    @Column(name = "puntos_ganados", nullable = false)
    private int puntosGanados = 0;

    @Version
    private Long version;

    // Lista de {tipo, entidadId} de las preguntas acertadas en este intento —
    // en un usuario logueado esto es redundante (ya se escribe en
    // TriviaPreguntaVista en tiempo real), pero en un invitado es la única
    // forma de reconstruir esas exclusiones cuando reclama el intento al
    // registrarse, ya que hasta ese momento no existe ningún User al que
    // atarlas.
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