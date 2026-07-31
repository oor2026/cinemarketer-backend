package com.example.demo.application.dtos;

import com.example.demo.domain.trivia.TriviaTipoPregunta;
import lombok.Data;

import java.util.List;

@Data
public class TriviaPreguntaPublicaDto {
    private TriviaTipoPregunta tipo;
    private String imagenUrl;
    private boolean mostrarPoster;
    private String sinopsis;
    private List<String> opciones;
    // sin "correcta" — nunca viaja al cliente antes de responder
}