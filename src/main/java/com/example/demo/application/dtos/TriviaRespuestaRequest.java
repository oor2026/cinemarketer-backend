package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class TriviaRespuestaRequest {
    private int opcionElegida;
    private int tiempoSegundos; // cuánto tardó en responder esta pregunta puntual
}