package com.example.demo.domain.banner;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "banners")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "link_destino", length = 500)
    private String linkDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "modulo", nullable = false, length = 50)
    private BannerModulo modulo;  // MI_CUENTA, MIS_PUNTOS, MIS_PREMIOS, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "posicion", nullable = false, length = 20)
    private BannerPosicion posicion;  // IZQUIERDO, DERECHO

    @Column(nullable = false)
    private Boolean visible = true;

    @Column(name = "nombre_marca", length = 200)
    private String nombreMarca;  // nombre descriptivo para identificar en el admin

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