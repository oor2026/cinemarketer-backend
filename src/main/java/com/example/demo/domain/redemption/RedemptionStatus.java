package com.example.demo.domain.redemption;

public enum RedemptionStatus {
    PENDING,     // Pendiente de entrega
    COORDINATED, // Ya se identificó, eligió punto de entrega — falta retirar/recibir
    COMPLETED,   // Completado/Entregado
    EXPIRED,     // Expirado
    CANCELLED    // Cancelado por el usuario
}