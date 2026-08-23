package com.example.demo.application.dtos;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class TriviaRankingDto {
    private Long userId;
    private int posicion;
    private String nombre;
    private int aciertos;
    private long tiempoTotalSegundos;
    private boolean esUsuarioActual;
}