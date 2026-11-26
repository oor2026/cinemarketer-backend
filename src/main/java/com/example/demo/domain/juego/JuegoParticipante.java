package com.example.demo.domain.juego;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Quien juega una campaña. Todo el juego (partidas, ranking, ganadores,
 * descalificaciones) se refiere al participante, no al usuario:
 *   - En campañas CINEMARKETER, userId apunta a su cuenta y nombre/email se
 *     copian de ella al entrar.
 *   - En campañas SIN_REGISTRO, userId es null: nombre y email son lo que la
 *     persona escribió al anotarse (no se valida el formato del email).
 */
@Entity
@Table(name = "juego_participantes")
public class JuegoParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campania_id", nullable = false)
    private Long campaniaId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String email;

    // Email en minúsculas y sin espacios: es lo que identifica al participante en la campaña.
    @Column(name = "email_normalizado", nullable = false, length = 200)
    private String emailNormalizado;

    @Column(name = "acepta_novedades", nullable = false)
    private Boolean aceptaNovedades = false;

    @Column(name = "acepta_bases_at")
    private LocalDateTime aceptaBasesAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean conCuenta() { return userId != null; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCampaniaId() { return campaniaId; }
    public void setCampaniaId(Long campaniaId) { this.campaniaId = campaniaId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEmailNormalizado() { return emailNormalizado; }
    public void setEmailNormalizado(String emailNormalizado) { this.emailNormalizado = emailNormalizado; }
    public Boolean getAceptaNovedades() { return aceptaNovedades; }
    public void setAceptaNovedades(Boolean aceptaNovedades) { this.aceptaNovedades = aceptaNovedades; }
    public LocalDateTime getAceptaBasesAt() { return aceptaBasesAt; }
    public void setAceptaBasesAt(LocalDateTime aceptaBasesAt) { this.aceptaBasesAt = aceptaBasesAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
