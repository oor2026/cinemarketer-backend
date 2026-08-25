package com.example.demo.domain.espiritu;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "espiritu_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class EspirituSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // "PELICULA" o "SERIE" — string plano, no enum, para no depender
    // de un paquete compartido nuevo entre domain.espiritu y domain.gusto.
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "fecha_snapshot", nullable = false)
    private LocalDateTime fechaSnapshot;

    @Column(name = "totem_nombre", nullable = false, length = 50)
    private String totemNombre;

    @Column(name = "genero_principal", nullable = false, length = 50)
    private String generoPrincipal;

    @Column(name = "porcentaje_principal", nullable = false)
    private Double porcentajePrincipal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}