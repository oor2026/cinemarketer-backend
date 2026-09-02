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
    private boolean nuncaJugo;  // true si este es su primer intento en la vida (solo usuarios logueados)
    private boolean jugoAyer;   // true si tiene un intento registrado con fecha = ayer
}
