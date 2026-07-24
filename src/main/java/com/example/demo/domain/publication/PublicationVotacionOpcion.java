package com.example.demo.domain.publication;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "publication_votacion_opciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationVotacionOpcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    @Column(nullable = false, length = 150)
    private String texto;

    @Column(name = "movie_id")
    private Long movieId;

    @Column(nullable = false)
    private int orden;
}