package com.example.demo.domain.trivia;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "trivia_preguntas_vistas")
@Data
public class TriviaPreguntaVista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriviaTipoPregunta tipo;

    // personId (si tipo=QUIEN_ES) o movieId (si tipo=PELICULA), ambos de TMDb
    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    // Valor de User.triviaRespondidasTotal al momento de acertarla — la
    // pregunta queda excluida mientras ese contador no llegue a este valor + 300
    @Column(name = "respondidas_total_al_acertar", nullable = false)
    private int respondidasTotalAlAcertar;
}