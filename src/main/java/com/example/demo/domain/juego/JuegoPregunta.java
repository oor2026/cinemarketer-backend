package com.example.demo.domain.juego;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una pregunta del banco de una campaña. Tiene de 2 a 4 opciones (opcion3 y
 * opcion4 pueden quedar nulas) e indiceCorrecta va de 1 a 4. La campaña se
 * referencia por id, sin relación JPA, para no cargarla en cada pregunta.
 * diaAsignado queda nulo hasta que se arman las tandas diarias.
 */
@Entity
@Table(name = "juego_preguntas")
public class JuegoPregunta {

    public static final String ORIGEN_MANUAL = "MANUAL";
    public static final String ORIGEN_TMDB = "TMDB";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campania_id", nullable = false)
    private Long campaniaId;

    @Column(nullable = false, length = 400)
    private String texto;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Column(name = "opcion_1", nullable = false, length = 200)
    private String opcion1;

    @Column(name = "opcion_2", nullable = false, length = 200)
    private String opcion2;

    @Column(name = "opcion_3", length = 200)
    private String opcion3;

    @Column(name = "opcion_4", length = 200)
    private String opcion4;

    @Column(name = "indice_correcta", nullable = false)
    private Integer indiceCorrecta;

    @Column(nullable = false, length = 10)
    private String origen = ORIGEN_MANUAL;

    @Column(name = "referencia_tmdb", length = 100)
    private String referenciaTmdb;

    @Column(name = "dia_asignado")
    private LocalDate diaAsignado;

    @Column(name = "orden_en_dia")
    private Integer ordenEnDia;

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

    // Las opciones cargadas, en orden (2 a 4).
    public List<String> opciones() {
        List<String> lista = new ArrayList<>();
        lista.add(opcion1);
        lista.add(opcion2);
        if (opcion3 != null) lista.add(opcion3);
        if (opcion4 != null) lista.add(opcion4);
        return lista;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCampaniaId() { return campaniaId; }
    public void setCampaniaId(Long campaniaId) { this.campaniaId = campaniaId; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public String getOpcion1() { return opcion1; }
    public void setOpcion1(String opcion1) { this.opcion1 = opcion1; }
    public String getOpcion2() { return opcion2; }
    public void setOpcion2(String opcion2) { this.opcion2 = opcion2; }
    public String getOpcion3() { return opcion3; }
    public void setOpcion3(String opcion3) { this.opcion3 = opcion3; }
    public String getOpcion4() { return opcion4; }
    public void setOpcion4(String opcion4) { this.opcion4 = opcion4; }
    public Integer getIndiceCorrecta() { return indiceCorrecta; }
    public void setIndiceCorrecta(Integer indiceCorrecta) { this.indiceCorrecta = indiceCorrecta; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public String getReferenciaTmdb() { return referenciaTmdb; }
    public void setReferenciaTmdb(String referenciaTmdb) { this.referenciaTmdb = referenciaTmdb; }
    public LocalDate getDiaAsignado() { return diaAsignado; }
    public void setDiaAsignado(LocalDate diaAsignado) { this.diaAsignado = diaAsignado; }
    public Integer getOrdenEnDia() { return ordenEnDia; }
    public void setOrdenEnDia(Integer ordenEnDia) { this.ordenEnDia = ordenEnDia; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
