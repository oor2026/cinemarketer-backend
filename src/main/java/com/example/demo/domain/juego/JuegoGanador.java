package com.example.demo.domain.juego;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Resultado del cierre: el ganador (puesto 1) y los suplentes.
 * Estados: PROPUESTO (antes de publicar), EN_ESPERA (suplente sin turno),
 * NOTIFICADO (tiene el premio y corre su plazo), RECLAMADO (lo pidió),
 * ENTREGADO, VENCIDO (no lo reclamó a tiempo) y DESCARTADO (lo sacó el admin).
 */
@Entity
@Table(name = "juego_ganadores")
public class JuegoGanador {

    public static final String GANADOR = "GANADOR";
    public static final String SUPLENTE = "SUPLENTE";
    public static final String PROPUESTO = "PROPUESTO";
    public static final String EN_ESPERA = "EN_ESPERA";
    public static final String NOTIFICADO = "NOTIFICADO";
    public static final String RECLAMADO = "RECLAMADO";
    public static final String ENTREGADO = "ENTREGADO";
    public static final String VENCIDO = "VENCIDO";
    public static final String DESCARTADO = "DESCARTADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campania_id", nullable = false)
    private Long campaniaId;

    @Column(name = "participante_id", nullable = false)
    private Long participanteId;

    @Column(nullable = false)
    private Integer puesto;

    @Column(nullable = false, length = 10)
    private String rol;

    @Column(nullable = false, length = 15)
    private String estado = PROPUESTO;

    @Column(length = 300)
    private String motivo;

    @Column(name = "notificado_at")
    private LocalDateTime notificadoAt;

    @Column(name = "reclamar_hasta")
    private LocalDateTime reclamarHasta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCampaniaId() { return campaniaId; }
    public void setCampaniaId(Long campaniaId) { this.campaniaId = campaniaId; }
    public Long getParticipanteId() { return participanteId; }
    public void setParticipanteId(Long participanteId) { this.participanteId = participanteId; }
    public Integer getPuesto() { return puesto; }
    public void setPuesto(Integer puesto) { this.puesto = puesto; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public LocalDateTime getNotificadoAt() { return notificadoAt; }
    public void setNotificadoAt(LocalDateTime notificadoAt) { this.notificadoAt = notificadoAt; }
    public LocalDateTime getReclamarHasta() { return reclamarHasta; }
    public void setReclamarHasta(LocalDateTime reclamarHasta) { this.reclamarHasta = reclamarHasta; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
