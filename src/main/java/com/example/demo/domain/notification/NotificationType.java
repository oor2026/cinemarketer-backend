package com.example.demo.domain.notification;

public enum NotificationType {
    BANCO,          // Alguien bancó tu comentario o respuesta
    MERECE_PUNTO,   // Alguien te dio un punto
    REPLY,           // Alguien respondió tu comentario
    COMMENT_REMOVED,
    NEW_FOLLOWER,
    RECOMMENDATION_RATED,
    NEW_RECOMMENDATION,     // Alguien te recomendó una película
    DRAW_WINNER,            // Ganaste un sorteo premium
    POINTS_RELEASED,        // Liberación mensual de puntos
    PREMIUM_EXPIRING_SOON,  // Tu suscripción vence en 7 días
    PREMIUM_EXPIRING_TOMORROW // Tu suscripción vence mañana
}
