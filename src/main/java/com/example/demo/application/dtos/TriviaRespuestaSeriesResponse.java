package com.example.demo.application.dtos;

import com.example.demo.domain.trivia.TriviaEstado;
import lombok.Data;

@Data
public class TriviaRespuestaSeriesResponse {
    private boolean correcta;
    private int respuestaCorrectaIndex;
    private int puntosGanadosEstaRespuesta;
    private int puntosGanadosTotal;
    private TriviaEstado estado;
    private int preguntaActual;
    private TriviaPreguntaSeriesPublicaDto siguientePregunta;
    private int aciertos;
}
