package com.example.demo.application.services;

import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Calcula el nivel de un usuario basándose únicamente en
 * los puntos canjeados históricos (totalRedeemedPoints).
 * El nivel es una insignia acumulativa — nunca baja.
 *
 * Umbrales:
 *   Amateur       → 0
 *   Colaborador   → 20.000 pts canjeados
 *   Crítico       → 40.000 pts canjeados
 *   Jurado Experto → 60.000 pts canjeados
 */
@Service
public class LevelCalculatorService {

    private final UserRepository userRepository;

    public LevelCalculatorService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Calcula el nivel correspondiente según puntos canjeados.
     * Si el usuario ya es JURADO_EXPERTO asignado por admin, lo mantiene.
     */
    public UserLevel calculateUserLevel(User user) {
        return UserLevel.getLevelByPoints(user.getTotalRedeemedPoints());
    }

    /**
     * Verifica si el usuario puede subir de nivel
     */
    public boolean canLevelUp(User user) {
        UserLevel currentLevel = user.getLevel();
        UserLevel nextLevel = currentLevel.getNextLevel();
        if (nextLevel == null) return false;
        return user.getTotalRedeemedPoints() >= nextLevel.getMinPoints();
    }

    /**
     * Obtiene el progreso hacia el siguiente nivel (0.0 - 100.0)
     */
    public double getProgressToNextLevel(User user) {
        UserLevel current = user.getLevel();
        UserLevel next = current.getNextLevel();
        if (next == null) return 100.0;

        int currentMin = current.getMinPoints();
        int nextMin = next.getMinPoints();
        int redeemed = user.getTotalRedeemedPoints();

        if (redeemed >= nextMin) return 100.0;
        if (redeemed <= currentMin) return 0.0;
        return ((double)(redeemed - currentMin) / (nextMin - currentMin)) * 100;
    }

    /**
     * Obtiene los puntos que faltan para el siguiente nivel
     */
    public int getPointsToNextLevel(User user) {
        UserLevel next = user.getLevel().getNextLevel();
        if (next == null) return 0;
        return Math.max(0, next.getMinPoints() - user.getTotalRedeemedPoints());
    }

    /**
     * Estadísticas de distribución de niveles
     */
    public Map<String, Long> getLevelStatistics() {
        Map<String, Long> stats = new HashMap<>();
        for (UserLevel level : UserLevel.values()) {
            stats.put(level.name(), userRepository.countByLevel(level));
        }
        return stats;
    }
}