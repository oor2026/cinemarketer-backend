package com.example.demo.application.dtos;

import com.example.demo.domain.trivia.TriviaTipoPreguntaSeries;
import lombok.Data;

import java.util.List;

@Data
public class TriviaPreguntaSeriesPublicaDto {
    private TriviaTipoPreguntaSeries tipo;
    private Integer temporadaNumero;
    private String serieNombre;
    private String imagenUrl;
    private boolean mostrarPoster;
    private String sinopsis;
    private List<String> opciones;
    // sin "correcta"
}
