package com.example.demo.domain.user;

/**
 * Niveles de usuario en Cinemarketer
 * Basados exclusivamente en puntos canjeados históricos (totalRedeemedPoints)
 * El nivel es una insignia acumulativa — nunca baja.
 */
public enum UserLevel {

    AMATEUR("Amateur", "🟢", 0),
    COLABORADOR("Colaborador", "🔵", 2000),
    CRITICO("Crítico", "🟣", 2000),
    JURADO_EXPERTO("Jurado Experto", "🏆", 10000);

    private final String displayName;
    private final String emoji;
    private final int minPoints;

    UserLevel(String displayName, String emoji, int minPoints) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.minPoints = minPoints;
    }

    public String getDisplayName() { return displayName; }
    public String getEmoji()       { return emoji; }
    public int getMinPoints()      { return minPoints; }

    public boolean isEligible(int redeemedPoints) {
        return redeemedPoints >= minPoints;
    }

    /**
     * Determina el nivel según puntos canjeados históricos.
     * Siempre devuelve el nivel más alto alcanzado.
     */
    public static UserLevel getLevelByPoints(int redeemedPoints) {
        if (redeemedPoints >= JURADO_EXPERTO.minPoints) return JURADO_EXPERTO;
        if (redeemedPoints >= CRITICO.minPoints)        return CRITICO;
        if (redeemedPoints >= COLABORADOR.minPoints)    return COLABORADOR;
        return AMATEUR;
    }

    public UserLevel getNextLevel() {
        switch (this) {
            case AMATEUR:       return COLABORADOR;
            case COLABORADOR:   return CRITICO;
            case CRITICO:       return JURADO_EXPERTO;
            case JURADO_EXPERTO: return null;
            default:            return null;
        }
    }

    public int getPointsToNextLevel(int currentRedeemedPoints) {
        UserLevel next = getNextLevel();
        if (next == null) return 0;
        return Math.max(0, next.minPoints - currentRedeemedPoints);
    }
}