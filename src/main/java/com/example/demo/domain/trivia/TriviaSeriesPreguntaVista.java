package com.example.demo.domain.trivia;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "trivia_series_preguntas_vistas")
@Data
public class TriviaSeriesPreguntaVista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriviaTipoPreguntaSeries tipo;

    // personId (QUIEN_ES/QUIEN_ES_TEMPORADA) o seriesId (SERIE/TEMPORADA_STILL), de TMDb
    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    // Solo relevante para QUIEN_ES_TEMPORADA y TEMPORADA_STILL — null en los otros 2
    @Column(name = "temporada_numero")
    private Integer temporadaNumero;

    @Column(name = "respondidas_total_al_acertar", nullable = false)
    private int respondidasTotalAlAcertar;
}
