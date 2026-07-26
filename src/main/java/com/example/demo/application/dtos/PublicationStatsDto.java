package com.example.demo.application.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class PublicationStatsDto {
    private long totalPublicaciones;
    private double growth;
    private double promedioPorDia;

    private long publicacionesTexto;
    private long publicacionesImagen;
    private long publicacionesVideo;
    private double porcentajeTexto;
    private double porcentajeImagen;
    private double porcentajeVideo;

    // Creator Tools — solo total del período, sin promedio.
    private long publicacionesFichaTecnica;
    private long publicacionesCountdown;
    private long publicacionesVotacion;
    private long publicacionesRanking;
    private long publicacionesTrivia;
    private long publicacionesTrailer;

    private double tasaAprobacionAutomatica;
    private long publicacionesEnRevision;
    private long publicacionesOcultasSancionadas;

    private long totalBanco;
    private double promedioBancoPorPublicacion;
    private long totalPuntos;
    private double promedioPuntosPorPublicacion;
    private long totalComentarios;
    private double promedioComentariosPorPublicacion;

    private List<Map<String, Object>> topUsuarios;
    private List<Map<String, Object>> topCategorias;
}