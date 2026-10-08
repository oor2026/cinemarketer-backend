package com.example.demo.domain.cinema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Precio de entrada cargado a mano para un cine, por formato y categoría.
 * Se usa para cines cuyo precio no se puede scrapear de una página (ej.
 * Cines Dino, que lo publica en un PDF semanal) o cuya web publica un valor
 * poco confiable (ej. Gran Rex). Se actualiza con SQL directo sobre la tabla;
 * CarteleraPreciosService la lee al actualizar los precios y tiene prioridad
 * sobre cualquier otra fuente.
 *
 * format: "2D", "3D", "MAGNIFY 8", "DOLBY ATMOS"...
 * category: "GENERAL" (la que se usa como precio de referencia), "MENORES",
 * "JUBILADOS_DISCAPACIDAD"...
 * validFrom: fecha desde la que rige el precio (la de la fuente), para
 * mostrar "Actualizado al dd/mm/aaaa".
 */
@Entity
@Table(name = "cinema_manual_prices",
        uniqueConstraints = @UniqueConstraint(name = "uq_cinema_manual_price",
                columnNames = {"cinema_id", "format", "category"}))
@Getter
@Setter
@NoArgsConstructor
public class CinemaManualPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(name = "format", nullable = false, length = 30)
    private String format;

    @Column(name = "category", nullable = false, length = 30)
    private String category = "GENERAL";

    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    // Días en que rige un 2x1 sobre este precio: nombres de java.time.DayOfWeek
    // separados por coma (ej. "WEDNESDAY" o "MONDAY,TUESDAY"). null = sin 2x1.
    @Column(name = "two_for_one_days", length = 40)
    private String twoForOneDays;

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