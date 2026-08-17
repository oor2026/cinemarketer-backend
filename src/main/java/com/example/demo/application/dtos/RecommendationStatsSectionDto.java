package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationStatsSectionDto {
    private long totalEnviadas;
    private long totalVistas;
    private double tasaVisualizacion;
    private long totalCalificadas;
    private double tasaCalificacion;
    private long totalConContexto;
    private double tasaContexto;
    private List<Map<String, Object>> topContent;
    private List<Map<String, Object>> topContextos;
}