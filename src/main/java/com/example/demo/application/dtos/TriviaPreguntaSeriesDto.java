package com.example.demo.application.dtos;

import com.example.demo.domain.trivia.TriviaTipoPreguntaSeries;
import lombok.Data;

import java.util.List;

@Data
public class TriviaPreguntaSeriesDto {
    private TriviaTipoPreguntaSeries tipo;
    private Long entidadId;          // personId si QUIEN_ES/QUIEN_ES_TEMPORADA, seriesId si SERIE/TEMPORADA_STILL
    private Integer temporadaNumero; // solo QUIEN_ES_TEMPORADA y TEMPORADA_STILL, null en los otros 2
    private String serieNombre;      // solo QUIEN_ES_TEMPORADA y TEMPORADA_STILL — da contexto de qué serie se pregunta
    private String imagenUrl;
    private boolean mostrarPoster;   // solo aplica a SERIE
    private String sinopsis;
    private List<String> opciones;
    private int correcta;
    private boolean respondida = false;
}
