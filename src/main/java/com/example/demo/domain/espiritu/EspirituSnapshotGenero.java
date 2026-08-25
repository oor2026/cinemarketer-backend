package com.example.demo.domain.espiritu;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "espiritu_snapshot_genero")
@Getter
@Setter
@NoArgsConstructor
public class EspirituSnapshotGenero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private EspirituSnapshot snapshot;

    @Column(nullable = false, length = 50)
    private String genero;

    @Column(nullable = false)
    private Integer puntos;

    @Column(nullable = false)
    private Double porcentaje;
}