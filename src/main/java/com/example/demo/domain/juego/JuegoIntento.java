package com.example.demo.domain.juego;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una partida: un usuario, una campaña, un día. La restricción única
 * (campania_id, user_id, dia) garantiza un solo intento por día.
 * tiempoTotalMs arranca con el máximo posible (preguntas × segundos) y baja
 * con cada respuesta rápida: así, cerrar la página no ahorra tiempo.
 */
@Entity
@Table(name = "juego_intentos")
public class JuegoIntento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campania_id", nullable = false)
    private Long campaniaId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate dia;

    @Column(name = "iniciado_at", nullable = false)
    private LocalDateTime iniciadoAt;

    @Column(name = "terminado_at")
    private LocalDateTime terminadoAt;

    @Column(nullable = false)
    private Integer aciertos = 0;

    @Column(name = "tiempo_total_ms", nullable = false)
    private Long tiempoTotalMs = 0L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCampaniaId() { return campaniaId; }
    public void setCampaniaId(Long campaniaId) { this.campaniaId = campaniaId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getDia() { return dia; }
    public void setDia(LocalDate dia) { this.dia = dia; }
    public LocalDateTime getIniciadoAt() { return iniciadoAt; }
    public void setIniciadoAt(LocalDateTime iniciadoAt) { this.iniciadoAt = iniciadoAt; }
    public LocalDateTime getTerminadoAt() { return terminadoAt; }
    public void setTerminadoAt(LocalDateTime terminadoAt) { this.terminadoAt = terminadoAt; }
    public Integer getAciertos() { return aciertos; }
    public void setAciertos(Integer aciertos) { this.aciertos = aciertos; }
    public Long getTiempoTotalMs() { return tiempoTotalMs; }
    public void setTiempoTotalMs(Long tiempoTotalMs) { this.tiempoTotalMs = tiempoTotalMs; }
}
