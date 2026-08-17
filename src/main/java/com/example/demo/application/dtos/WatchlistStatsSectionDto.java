package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistStatsSectionDto {
    private long totalGuardadas;
    private long usuariosConLista;
    private double promedioPorUsuario;
    private List<Map<String, Object>> topContent;
    private List<Map<String, Object>> generos;
}