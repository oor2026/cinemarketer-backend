package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoVistasStatsSectionDto {
    private long totalOmitidas;
    private double growth;
    // Top 10 — a propósito SIN filtro de fecha, ver AdminStatsController:
    // mide el estado actual ("quiénes la tienen marcada como no vista
    // HOY"), no lo que pasó en el período elegido en el filtro.
    private List<Map<String, Object>> topOmitidas;
    private Map<String, Long> dailyTrend;
}