package com.example.demo.application.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatsDto {
    private long totalSuscripciones;
    private long suscripcionesActivas;
    private long suscripcionesCanceladas;
    private long suscripcionesPendientes;
    private long nuevasSuscripciones;
    private long usuariosSuscriptos;
}