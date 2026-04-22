package com.example.demo.domain.sweepstake;

public enum WinnerStatus {
    PENDING,    // Pendiente de respuesta
    ACCEPTED,   // Aceptó el premio
    REJECTED,   // Rechazó el premio
    EXPIRED     // No respondió en tiempo
}