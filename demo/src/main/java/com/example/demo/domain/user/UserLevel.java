package com.example.demo.domain.user;

/**
 * Niveles de usuario en Cinemarketer
 * Basados en la actividad y participación del usuario
 */
public enum UserLevel {

    /**
     * Nivel inicial para usuarios recién registrados
     */
    AMATEUR("Amateur", "🟢", 0),

    /**
     * Usuarios con participación activa (votos y comentarios)
     */
    COLABORADOR("Colaborador", "🔵", 3000),

    /**
     * Usuarios con alta participación y calidad en comentarios
     */
    CRITICO("Crítico", "🟣", 5000),

    /**
     * Usuarios destacados (solo asignable por admin)
     */
    JURADO_EXPERTO("Jurado Experto", "🏆", 12500);

    private final String displayName;
    private final String emoji;
    private final int minPoints;

    UserLevel(String displayName, String emoji, int minPoints) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.minPoints = minPoints;
    }

    /**
     * Obtiene el nombre legible para mostrar en UI
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Obtiene el emoji representativo del nivel
     */
    public String getEmoji() {
        return emoji;
    }

    /**
     * Obtiene los puntos mínimos requeridos para este nivel
     */
    public int getMinPoints() {
        return minPoints;
    }

    /**
     * Determina si un usuario puede alcanzar este nivel según sus puntos
     */
    public boolean isEligible(int userPoints) {
        return userPoints >= minPoints;
    }

    /**
     * Obtiene el nivel correspondiente a un puntaje
     */
    public static UserLevel getLevelByPoints(int points) {
        if (points >= JURADO_EXPERTO.minPoints) {
            return JURADO_EXPERTO;
        } else if (points >= CRITICO.minPoints) {
            return CRITICO;
        } else if (points >= COLABORADOR.minPoints) {
            return COLABORADOR;
        } else {
            return AMATEUR;
        }
    }

    /**
     * Obtiene el siguiente nivel al que puede aspirar el usuario
     */
    public UserLevel getNextLevel() {
        switch (this) {
            case AMATEUR:
                return COLABORADOR;
            case COLABORADOR:
                return CRITICO;
            case CRITICO:
                return JURADO_EXPERTO;
            case JURADO_EXPERTO:
                return null; // No hay nivel superior
            default:
                return null;
        }
    }

    /**
     * Obtiene los puntos faltantes para alcanzar el siguiente nivel
     */
    public int getPointsToNextLevel(int currentPoints) {
        UserLevel next = getNextLevel();
        if (next == null) {
            return 0; // Ya está en el máximo
        }
        return Math.max(0, next.minPoints - currentPoints);
    }
}