package com.example.demo.domain.subscription;

public enum SubscriptionStatus {
    PENDING,    // pendiente de primer pago
    ACTIVE,     // activa y al día
    CANCELLED,  // cancelada por el usuario
    EXPIRED     // venció sin renovación
}
