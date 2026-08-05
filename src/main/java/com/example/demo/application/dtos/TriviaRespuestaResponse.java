package com.example.demo.application.dtos;

import com.example.demo.domain.trivia.TriviaEstado;
import lombok.Data;

@Data
public class TriviaRespuestaResponse {
    private boolean correcta;
    private int respuestaCorrectaIndex; // recién se revela acá, después de contestar
    private int puntosGanadosEstaRespuesta; // 0 o 5, para el toast
    private int puntosGanadosTotal;         // acumulado del intento de hoy
    private TriviaEstado estado;
    private int preguntaActual;
    private TriviaPreguntaPublicaDto siguientePregunta; // null si el intento terminó
    private int aciertos; // total de respuestas correctas del intento, hasta ahora
}