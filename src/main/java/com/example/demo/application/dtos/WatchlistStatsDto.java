package com.example.demo.application.dtos;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WatchlistStatsDto {
    private long totalGuardadas;
    private long usuariosConLista;
    private double promedioPorUsuario;
    private List<Map<String, Object>> topPeliculas;
    private List<Map<String, Object>> generos;
}