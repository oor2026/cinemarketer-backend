package com.example.demo.application.dtos;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class RevenueStatsDto {
    private BigDecimal ingresoTotalHistorico;
    private BigDecimal ingresoPeriodo;
    private BigDecimal mrr;                          // ingreso mensual recurrente
    private long pagosAprobadosPeriodo;
    private long pagosRechazadosPeriodo;
    private long pagosPendientesPeriodo;
    private long pagosAprobadosTotal;
    private double tasaAprobacion;
    private List<Map<String, Object>> tendenciaMensual;
}