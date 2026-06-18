package com.example.demo.application.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class RecommendationStatsDto {
    private long totalEnviadas;
    private long totalVistas;
    private double tasaVisualizacion;
    private long totalCalificadas;
    private double tasaCalificacion;
    private long totalConContexto;
    private double tasaContexto;
    private List<Map<String, Object>> topPeliculas;
    private List<Map<String, Object>> topContextos;
}