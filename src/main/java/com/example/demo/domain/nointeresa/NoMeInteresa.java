package com.example.demo.domain.nointeresa;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

// "No me interesa" — señal pura de descubrimiento (qué no volver a
// mostrar en el feed). A propósito NO toca votos, comentarios, puntos
// ni notificaciones: si el usuario quiere revertir su opinión sobre
// la película en sí, ya existen las herramientas correctas para eso
// (cambiar el voto, ocultar el comentario) — esto es otra cosa.
@Entity
@Table(name = "no_me_interesa", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
public class NoMeInteresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}