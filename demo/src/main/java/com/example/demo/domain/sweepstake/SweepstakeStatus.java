package com.example.demo.domain.sweepstake;

public enum SweepstakeStatus {
    DRAFT,      // En borrador, no visible
    ACTIVE,     // Activo, los usuarios pueden participar
    FINISHED,   // Finalizado, se realizó el sorteo
    CANCELLED   // Cancelado
}