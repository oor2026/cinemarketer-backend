package com.example.demo.domain.juego;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Centro de actividad — una campaña de juego con premio (v1: Trivia).
 * Las fechas se guardan en hora de Argentina, como el resto del sistema.
 * El estado ACTIVA y CERRADA lo pone el servidor según las fechas; el admin
 * solo publica (BORRADOR → PROGRAMADA), confirma ganadores y archiva.
 */
@Entity
@Table(name = "juego_campanias")
public class JuegoCampania {

    public static final String TIPO_TRIVIA = "TRIVIA";

    public static final String MODO_CINEMARKETER = "CINEMARKETER";
    public static final String MODO_SIN_REGISTRO = "SIN_REGISTRO";

    public static final String BORRADOR = "BORRADOR";
    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String ACTIVA = "ACTIVA";
    public static final String CERRADA = "CERRADA";
    public static final String PUBLICADA = "PUBLICADA";
    public static final String ARCHIVADA = "ARCHIVADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 300)
    private String bajada;

    @Column(name = "tipo_juego", nullable = false, length = 20)
    private String tipoJuego = TIPO_TRIVIA;

    @Column(nullable = false, length = 20)
    private String estado = BORRADOR;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime cierre;

    @Column(name = "preguntas_por_dia", nullable = false)
    private Integer preguntasPorDia = 10;

    @Column(name = "segundos_por_pregunta", nullable = false)
    private Integer segundosPorPregunta = 15;

    @Column(name = "premio_texto", length = 300)
    private String premioTexto;

    @Column(name = "premio_imagen_url", length = 500)
    private String premioImagenUrl;

    // Opcional: premio del Club, para entregarlo por el Centro de autogestión.
    @Column(name = "reward_id")
    private Long rewardId;

    // Cuántos se llevan el premio: los primeros N del ranking.
    @Column(name = "cantidad_ganadores", nullable = false)
    private Integer cantidadGanadores = 1;

    @Column(name = "cantidad_suplentes", nullable = false)
    private Integer cantidadSuplentes = 2;

    @Column(name = "dias_para_reclamar", nullable = false)
    private Integer diasParaReclamar = 7;

    @Column(name = "bases_condiciones", columnDefinition = "TEXT")
    private String basesCondiciones;

    // Detalle del premio: en la tarjeta se ve recortado, completo en el modal.
    @Column(name = "premio_descripcion", columnDefinition = "TEXT")
    private String premioDescripcion;

    @Column(name = "fondo_url", length = 500)
    private String fondoUrl;

    @Column(name = "color_acento", length = 7)
    private String colorAcento;

    // Imágenes de la landing (se suben desde el admin). fondoUrl es el banner
    // de escritorio; el de celular y el fondo de los costados son opcionales.
    @Column(name = "banner_mobile_url", length = 500)
    private String bannerMobileUrl;

    @Column(name = "fondo_lateral_url", length = 500)
    private String fondoLateralUrl;

    // Pieza "Acceso": cómo se identifica el jugador.
    //   CINEMARKETER  → con su cuenta de Cinemarketer (como siempre).
    //   SIN_REGISTRO  → solo nombre y email, sin cuenta.
    @Column(name = "modo_acceso", nullable = false, length = 20)
    private String modoAcceso = MODO_CINEMARKETER;

    // Solo SIN_REGISTRO: muestra el check opcional "Quiero recibir novedades de Cinemarketer".
    @Column(name = "pedir_novedades", nullable = false)
    private Boolean pedirNovedades = true;

    // Solo para campañas archivadas: si está en true, la landing ya no muestra nada.
    @Column(name = "landing_oculta", nullable = false)
    private Boolean landingOculta = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getBajada() { return bajada; }
    public void setBajada(String bajada) { this.bajada = bajada; }
    public String getTipoJuego() { return tipoJuego; }
    public void setTipoJuego(String tipoJuego) { this.tipoJuego = tipoJuego; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getInicio() { return inicio; }
    public void setInicio(LocalDateTime inicio) { this.inicio = inicio; }
    public LocalDateTime getCierre() { return cierre; }
    public void setCierre(LocalDateTime cierre) { this.cierre = cierre; }
    public Integer getPreguntasPorDia() { return preguntasPorDia; }
    public void setPreguntasPorDia(Integer preguntasPorDia) { this.preguntasPorDia = preguntasPorDia; }
    public Integer getSegundosPorPregunta() { return segundosPorPregunta; }
    public void setSegundosPorPregunta(Integer segundosPorPregunta) { this.segundosPorPregunta = segundosPorPregunta; }
    public String getPremioTexto() { return premioTexto; }
    public void setPremioTexto(String premioTexto) { this.premioTexto = premioTexto; }
    public String getPremioImagenUrl() { return premioImagenUrl; }
    public void setPremioImagenUrl(String premioImagenUrl) { this.premioImagenUrl = premioImagenUrl; }
    public Long getRewardId() { return rewardId; }
    public void setRewardId(Long rewardId) { this.rewardId = rewardId; }
    public Integer getCantidadGanadores() { return cantidadGanadores; }
    public void setCantidadGanadores(Integer cantidadGanadores) { this.cantidadGanadores = cantidadGanadores; }
    public Integer getCantidadSuplentes() { return cantidadSuplentes; }
    public void setCantidadSuplentes(Integer cantidadSuplentes) { this.cantidadSuplentes = cantidadSuplentes; }
    public Integer getDiasParaReclamar() { return diasParaReclamar; }
    public void setDiasParaReclamar(Integer diasParaReclamar) { this.diasParaReclamar = diasParaReclamar; }
    public String getBasesCondiciones() { return basesCondiciones; }
    public void setBasesCondiciones(String basesCondiciones) { this.basesCondiciones = basesCondiciones; }
    public String getPremioDescripcion() { return premioDescripcion; }
    public void setPremioDescripcion(String premioDescripcion) { this.premioDescripcion = premioDescripcion; }
    public String getFondoUrl() { return fondoUrl; }
    public void setFondoUrl(String fondoUrl) { this.fondoUrl = fondoUrl; }
    public String getColorAcento() { return colorAcento; }
    public void setColorAcento(String colorAcento) { this.colorAcento = colorAcento; }
    public String getBannerMobileUrl() { return bannerMobileUrl; }
    public void setBannerMobileUrl(String bannerMobileUrl) { this.bannerMobileUrl = bannerMobileUrl; }
    public String getFondoLateralUrl() { return fondoLateralUrl; }
    public void setFondoLateralUrl(String fondoLateralUrl) { this.fondoLateralUrl = fondoLateralUrl; }
    public String getModoAcceso() { return modoAcceso; }
    public void setModoAcceso(String modoAcceso) { this.modoAcceso = modoAcceso; }
    public Boolean getPedirNovedades() { return pedirNovedades; }
    public void setPedirNovedades(Boolean pedirNovedades) { this.pedirNovedades = pedirNovedades; }
    public boolean sinRegistro() { return MODO_SIN_REGISTRO.equals(modoAcceso); }

    public Boolean getLandingOculta() { return landingOculta; }
    public void setLandingOculta(Boolean landingOculta) { this.landingOculta = landingOculta; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
