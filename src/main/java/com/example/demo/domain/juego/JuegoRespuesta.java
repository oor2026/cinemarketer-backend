package com.example.demo.domain.juego;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Una pregunta entregada en una partida. El tiempo lo mide el servidor:
 * desde entregadaAt hasta respondidaAt, con tope en los segundos de la campaña.
 * Sin respuesta (respondidaAt nulo) es la pregunta que está en pantalla.
 */
@Entity
@Table(name = "juego_respuestas")
public class JuegoRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intento_id", nullable = false)
    private Long intentoId;

    @Column(name = "pregunta_id", nullable = false)
    private Long preguntaId;

    @Column(nullable = false)
    private Integer orden;

    @Column(name = "entregada_at", nullable = false)
    private LocalDateTime entregadaAt;

    @Column(name = "respondida_at")
    private LocalDateTime respondidaAt;

    @Column(name = "opcion_elegida")
    private Integer opcionElegida;

    @Column(nullable = false)
    private Boolean correcta = false;

    @Column(name = "tiempo_ms")
    private Long tiempoMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIntentoId() { return intentoId; }
    public void setIntentoId(Long intentoId) { this.intentoId = intentoId; }
    public Long getPreguntaId() { return preguntaId; }
    public void setPreguntaId(Long preguntaId) { this.preguntaId = preguntaId; }
    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
    public LocalDateTime getEntregadaAt() { return entregadaAt; }
    public void setEntregadaAt(LocalDateTime entregadaAt) { this.entregadaAt = entregadaAt; }
    public LocalDateTime getRespondidaAt() { return respondidaAt; }
    public void setRespondidaAt(LocalDateTime respondidaAt) { this.respondidaAt = respondidaAt; }
    public Integer getOpcionElegida() { return opcionElegida; }
    public void setOpcionElegida(Integer opcionElegida) { this.opcionElegida = opcionElegida; }
    public Boolean getCorrecta() { return correcta; }
    public void setCorrecta(Boolean correcta) { this.correcta = correcta; }
    public Long getTiempoMs() { return tiempoMs; }
    public void setTiempoMs(Long tiempoMs) { this.tiempoMs = tiempoMs; }
}
