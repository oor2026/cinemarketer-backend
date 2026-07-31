package com.example.demo.application.dtos;

import com.example.demo.domain.trivia.TriviaTipoPregunta;
import lombok.Data;

import java.util.List;

@Data
public class TriviaPreguntaDto {
    private TriviaTipoPregunta tipo;
    private Long entidadId;       // personId si QUIEN_ES, movieId si PELICULA
    private String imagenUrl;     // null si es la variante "sinopsis" de PELICULA
    private boolean mostrarPoster; // solo aplica a PELICULA
    private String sinopsis;      // solo si mostrarPoster = false
    private List<String> opciones;
    private int correcta;         // índice dentro de opciones
    private boolean respondida = false; // se completa al procesar la respuesta, no al generar
}