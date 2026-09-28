package com.example.demo.application.dtos;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProximosEstrenosStatsDto {
    private long totalRespuestas;
    private long esperan;
    private long noEsperan;
    private double pctEsperan;
    private double pctNoEsperan;
    private long usuariosDistintos;
    private long avisosActivados;
    private double pctAvisos; // sobre los "Sí"
    private List<Map<String, Object>> topMasEsperadas;
    private List<Map<String, Object>> topMenosEsperadas;
    private List<Map<String, Object>> topUsuarios;
}