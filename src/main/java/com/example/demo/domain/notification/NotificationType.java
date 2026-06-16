package com.example.demo.domain.notification;

public enum NotificationType {
    BANCO,          // Alguien bancó tu comentario o respuesta
    MERECE_PUNTO,   // Alguien te dio un punto
    REPLY,           // Alguien respondió tu comentario
    COMMENT_REMOVED,
    NEW_FOLLOWER,
    FOLLOW_REQUEST,       // Alguien quiere seguirte (perfil privado)
    FOLLOW_REQUEST_ACCEPTED, // Aceptaron tu solicitud de seguimiento
    RECOMMENDATION_RATED,
    NEW_RECOMMENDATION,     // Alguien te recomendó una película
    DRAW_WINNER,            // Ganaste un sorteo premium
    POINTS_RELEASED,        // Liberación mensual de puntos
    PREMIUM_EXPIRING_SOON,  // Tu suscripción vence en 7 días
    PREMIUM_EXPIRING_TOMORROW, // Tu suscripción vence mañana
    INSIGNIA_ASCENSO,          // El usuario subió de insignia
    INSIGNIA_PREMIO            // Puntos de regalo por ascenso de insignia
}
