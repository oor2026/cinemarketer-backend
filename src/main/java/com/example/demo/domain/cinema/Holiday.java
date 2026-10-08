package com.example.demo.domain.cinema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Feriado nacional. Lo usa CarteleraPreciosService para las tarifas que no
 * valen en feriado (ej. el precio de lunes a miércoles de Atlas, que no rige
 * en los lunes o martes feriados). Se carga con SQL directo sobre la tabla.
 * Los días no laborables con fines turísticos NO son feriados: no van acá.
 */
@Entity
@Table(name = "holidays",
        uniqueConstraints = @UniqueConstraint(name = "uq_holiday_date", columnNames = {"holiday_date"}))
@Getter
@Setter
@NoArgsConstructor
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", length = 120)
    private String name;
}
