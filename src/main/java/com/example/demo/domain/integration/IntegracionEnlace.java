package com.example.demo.domain.integracion;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Integraciones — un enlace de una cadena de cine hacia una sección de su sitio:
 * sus sorteos (tipo SORTEO) o sus promociones (tipo PROMO_CADENA). Cinemarketer
 * no copia el contenido: muestra la cadena con su logo y redirige al sitio oficial,
 * contando cuántas veces se tocó (clics), que es el dato para la conversación
 * comercial con la marca.
 *
 * Las promociones por medio de pago (banco, tarjeta, billetera) NO van acá: son
 * datos estructurados y tienen su propia tabla.
 */
@Entity
@Table(name = "integracion_enlaces")
@Data
@NoArgsConstructor
public class IntegracionEnlace {

    public static final String TIPO_SORTEO = "SORTEO";
    public static final String TIPO_PROMO_CADENA = "PROMO_CADENA";

    // Estado del vínculo con la marca: solo para uso interno del admin.
    public static final String VINCULO_PUBLICO = "PUBLICO";       // tomado de su sitio, sin contacto
    public static final String VINCULO_CONTACTADO = "CONTACTADO"; // ya hablamos con la marca
    public static final String VINCULO_ACUERDO = "ACUERDO";       // hay acuerdo de trabajo conjunto

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String tipo;

    // Nombre de la cadena tal como se muestra en Cartelera ("Showcase", "Cinemark"...):
    // con este nombre el front busca el logo.
    @Column(nullable = false, length = 100)
    private String cadena;

    // Texto opcional del botón ("Participá en los sorteos de Showcase").
    @Column(length = 150)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private Boolean activo = false;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(name = "estado_vinculo", nullable = false, length = 20)
    private String estadoVinculo = VINCULO_PUBLICO;

    @Column(name = "nota_interna", length = 1000)
    private String notaInterna;

    @Column(nullable = false)
    private Long clics = 0L;

    // Última vez que se revisó que el enlace funciona.
    @Column(name = "verificado_en")
    private LocalDate verificadoEn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
