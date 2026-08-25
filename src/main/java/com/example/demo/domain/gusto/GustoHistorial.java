package com.example.demo.domain.gusto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "gusto_historial")
@Getter
@Setter
@NoArgsConstructor
public class GustoHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String tipo; // PELICULA / SERIE

    @Column(nullable = false, length = 20)
    private String campo; // FAVORITA / VISTA_CINE / NO_ME_CANSO / NO_LA_BANCO

    @Column(name = "contenido_id")
    private Long contenidoId;

    @Column(name = "contenido_titulo", length = 255)
    private String contenidoTitulo;

    @Column(name = "contenido_poster", length = 255)
    private String contenidoPoster;

    @Column(name = "fecha_detectado", nullable = false)
    private LocalDateTime fechaDetectado;
}