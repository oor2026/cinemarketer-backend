package com.example.demo.application.dtos;

import com.example.demo.domain.trivia.TriviaEstado;
import lombok.Data;

@Data
public class TriviaEstadoSeriesResponse {
    private TriviaEstado estado;
    private int preguntaActual;
    private int totalPreguntas;
    private int puntosGanados;
    private TriviaPreguntaSeriesPublicaDto pregunta;
    private int aciertos;
}
