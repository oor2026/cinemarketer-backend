package com.example.demo.domain.moderation;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_moderation")
@Data
@NoArgsConstructor
public class ImageModeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, unique = true, length = 500)
    private String imageUrl;

    @Column(name = "score_neutral")
    private Double scoreNeutral;

    @Column(name = "score_drawing")
    private Double scoreDrawing;

    @Column(name = "score_sexy")
    private Double scoreSexy;

    @Column(name = "score_porn")
    private Double scorePorn;

    @Column(name = "score_hentai")
    private Double scoreHentai;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_riesgo", nullable = false, length = 20)
    private NivelRiesgoImagen nivelRiesgo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}