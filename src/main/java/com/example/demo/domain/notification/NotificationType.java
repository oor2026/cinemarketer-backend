package com.example.demo.domain.notification;

public enum NotificationType {
    BANCO,                    // Alguien bancó tu comentario en película
    MERECE_PUNTO,             // Alguien te dio un punto en comentario de película
    REPLY,                    // Alguien respondió tu comentario en película
    PUB_BANCO,                // Alguien bancó tu publicación
    PUB_MERECE_PUNTO,         // Alguien te dio un punto en tu publicación
    PUB_COMENTARIO,           // Alguien comentó tu publicación
    PUB_BANCO_COMENTARIO,     // Alguien bancó tu comentario en una publicación
    PUB_RESPUESTA,            // Alguien respondió tu comentario en una publicación
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
    INSIGNIA_PREMIO,            // Puntos de regalo por ascenso de insignia
    ADMIN_GRANT_POINTS,        // Admin otorgó puntos manualmente
    NEW_REWARD,                // Nuevo premio común disponible
    NEW_PREMIUM_REWARD,        // Nuevo premio premium disponible
    PUB_APROBADA,              // Publicación aprobada (auto por sistema, o manual por admin en Pendientes)
    PUB_PENDIENTE_REVISION,    // Publicación (imagen o video) quedó pendiente de revisión por moderación automática
    VIDEO_APROBADO,            // Video agregado por edición a una publicación ya viva, aprobado
    VIDEO_PENDIENTE_REVISION,  // Video agregado por edición a una publicación ya viva, pendiente de revisión
    VIDEO_RECHAZADO            // Admin rechazó el video (Caso B) — la publicación en sí sigue viva
}
