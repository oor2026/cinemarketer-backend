package com.example.demo.application.dtos;

import com.example.demo.domain.trivia.TriviaEstado;
import lombok.Data;

@Data
public class TriviaEstadoResponse {
    private TriviaEstado estado;
    private int preguntaActual;   // 1-indexed, para mostrar "Pregunta 4 de 10"
    private int totalPreguntas;
    private int puntosGanados;
    private TriviaPreguntaPublicaDto pregunta; // null si no está EN_CURSO
    private int aciertos; // total de respuestas correctas del intento (si ya terminó)
    private boolean nuncaJugo;  // true si este es su primer intento en la vida (solo usuarios logueados)
    private boolean jugoAyer;   // true si tiene un intento registrado con fecha = ayer
}