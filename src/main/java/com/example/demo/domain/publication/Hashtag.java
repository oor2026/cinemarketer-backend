package com.example.demo.domain.publication;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "hashtags")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ya llega normalizado por normalizarHashtags() de PublicationService:
    // minúsculas, sin "#", solo [a-z0-9áéíóúñ_]. Es la única fuente de verdad,
    // así que acá no se vuelve a normalizar, se asume ya limpio.
    @Column(name = "nombre", nullable = false, unique = true, length = 60)
    private String nombre;

    @Column(name = "usage_count", nullable = false)
    private int usageCount = 0;
}