package com.example.demo.domain.publication;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "publication_ranking_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationRankingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    // Ranking siempre es de películas — a diferencia de Votación, acá el
    // movieId es obligatorio, nunca texto libre solo.
    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    // Solo tiene contenido si rankingModoTexto = SEGMENTADA. En modo
    // ESTANDAR queda null — la opinión general vive en Publication.content.
    @Column(columnDefinition = "TEXT")
    private String texto;

    // El puesto — fijo según el orden en que el Creator lo agregó, sin
    // reordenamiento posterior.
    @Column(nullable = false)
    private int orden;
}