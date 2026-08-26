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
    // Distribución de "¿por qué la guardaste?" — solo cuenta las que
    // tienen motivo (el modal es opcional, muchas van a quedar null).
    private List<Map<String, Object>> motivos;
    // % de guardadas que sí respondieron el motivo, sobre el total —
    // mide qué tanto se usa esa pregunta opcional, no solo qué contestan.
    private double pctConMotivo;
}