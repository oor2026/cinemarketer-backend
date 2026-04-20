package com.example.demo.application.services;

import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.pointtransaction.PointTransactionRepository;
import com.example.demo.domain.pointtransaction.PointTransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para calcular y gestionar los niveles de usuario
 * Basado en puntos gastados, votos, comentarios y días desde registro hasta último login
 */
@Service
public class LevelCalculatorService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;
    private final PointTransactionRepository pointTransactionRepository;

    // Configuración de niveles con nuevos valores
    private static final Map<UserLevel, LevelConfig> LEVEL_CONFIG = new HashMap<>();

    static {
        // Configuración de cada nivel según requisitos del cliente
        LEVEL_CONFIG.put(UserLevel.AMATEUR, new LevelConfig(
                "Amateur",
                0,              // puntos gastados mínimos
                0,              // votos mínimos
                0,              // comentarios mínimos
                0,              // días de antigüedad mínimos (desde registro hasta último login)
                "Usuario recién registrado"
        ));

        LEVEL_CONFIG.put(UserLevel.COLABORADOR, new LevelConfig(
                "Colaborador",
                3000,           // puntos gastados mínimos
                500,            // votos mínimos
                50,             // comentarios mínimos
                100,            // 100 días desde registro hasta último login
                "Usuario con participación activa"
        ));

        LEVEL_CONFIG.put(UserLevel.CRITICO, new LevelConfig(
                "Crítico",
                5000,           // puntos gastados mínimos
                800,            // votos mínimos
                100,            // comentarios mínimos
                170,            // 170 días desde registro hasta último login
                "Usuario con alta participación y calidad"
        ));

        LEVEL_CONFIG.put(UserLevel.JURADO_EXPERTO, new LevelConfig(
                "Jurado Experto",
                12500,          // puntos gastados mínimos
                1500,           // votos mínimos
                500,            // comentarios mínimos
                365,            // 365 días (1 año) desde registro hasta último login
                "Usuario destacado"
        ));
    }

    public LevelCalculatorService(UserRepository userRepository,
                                  ReviewRepository reviewRepository,
                                  CommentRepository commentRepository,
                                  PointTransactionRepository pointTransactionRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
        this.pointTransactionRepository = pointTransactionRepository;
    }

    // ==============================================
    // CÁLCULO DE NIVEL
    // ==============================================

    /**
     * Calcula el nivel de un usuario basado en:
     * - Puntos gastados (no acumulados)
     * - Votos realizados
     * - Comentarios publicados
     * - Días desde registro hasta último login
     */
    public UserLevel calculateUserLevel(User user) {
        // Si es Jurado Experto asignado manualmente, mantenerlo
        if (user.getLevel() == UserLevel.JURADO_EXPERTO && user.getRole().isAdmin()) {
            return UserLevel.JURADO_EXPERTO;
        }

        int spentPoints = getTotalSpentPoints(user.getId());
        long votes = reviewRepository.countByUserId(user.getId());
        long comments = commentRepository.countByUserId(user.getId());
        long activeDays = calculateActiveDays(user);

        // Verificar cada nivel de mayor a menor
        if (meetsJuradoExpertoRequirements(spentPoints, votes, comments, activeDays)) {
            return UserLevel.JURADO_EXPERTO;
        } else if (meetsCriticoRequirements(spentPoints, votes, comments, activeDays)) {
            return UserLevel.CRITICO;
        } else if (meetsColaboradorRequirements(spentPoints, votes, comments, activeDays)) {
            return UserLevel.COLABORADOR;
        } else {
            return UserLevel.AMATEUR;
        }
    }

    /**
     * Obtiene el total de puntos gastados por el usuario
     */
    private int getTotalSpentPoints(Long userId) {
        Integer total = pointTransactionRepository.sumPointsByUserAndType(userId, PointTransactionType.SPENT);
        return total != null ? total : 0;
    }

    /**
     * Calcula los días activos (desde created_at hasta last_login_at)
     */
    private long calculateActiveDays(User user) {
        if (user.getCreatedAt() == null) {
            return 0;
        }

        LocalDateTime endDate = user.getLastLoginAt() != null ?
                user.getLastLoginAt() :
                LocalDateTime.now();

        return ChronoUnit.DAYS.between(user.getCreatedAt(), endDate);
    }

    /**
     * Verifica si cumple requisitos de Colaborador
     */
    private boolean meetsColaboradorRequirements(int spentPoints, long votes, long comments, long activeDays) {
        LevelConfig config = LEVEL_CONFIG.get(UserLevel.COLABORADOR);
        return spentPoints >= config.minPoints &&
                votes >= config.minVotes &&
                comments >= config.minComments &&
                activeDays >= config.minActiveDays;
    }

    /**
     * Verifica si cumple requisitos de Crítico
     */
    private boolean meetsCriticoRequirements(int spentPoints, long votes, long comments, long activeDays) {
        LevelConfig config = LEVEL_CONFIG.get(UserLevel.CRITICO);
        return spentPoints >= config.minPoints &&
                votes >= config.minVotes &&
                comments >= config.minComments &&
                activeDays >= config.minActiveDays;
    }

    /**
     * Verifica si cumple requisitos de Jurado Experto
     */
    private boolean meetsJuradoExpertoRequirements(int spentPoints, long votes, long comments, long activeDays) {
        LevelConfig config = LEVEL_CONFIG.get(UserLevel.JURADO_EXPERTO);
        return spentPoints >= config.minPoints &&
                votes >= config.minVotes &&
                comments >= config.minComments &&
                activeDays >= config.minActiveDays;
    }

    // ==============================================
    // MÉTODOS DE UTILIDAD
    // ==============================================

    /**
     * Obtiene la configuración de un nivel
     */
    public LevelConfig getLevelConfig(UserLevel level) {
        return LEVEL_CONFIG.get(level);
    }

    /**
     * Obtiene el progreso hacia el siguiente nivel
     */
    public LevelProgress getProgressToNextLevel(User user) {
        UserLevel currentLevel = user.getLevel();
        UserLevel nextLevel = currentLevel.getNextLevel();

        if (nextLevel == null) {
            return new LevelProgress(currentLevel, null, 100.0, 0, 0, 0, 0);
        }

        LevelConfig currentConfig = LEVEL_CONFIG.get(currentLevel);
        LevelConfig nextConfig = LEVEL_CONFIG.get(nextLevel);

        int spentPoints = getTotalSpentPoints(user.getId());
        long votes = reviewRepository.countByUserId(user.getId());
        long comments = commentRepository.countByUserId(user.getId());
        long activeDays = calculateActiveDays(user);

        // Calcular porcentajes de cada requisito
        double pointsProgress = calculateProgress(spentPoints, currentConfig.minPoints, nextConfig.minPoints);
        double votesProgress = calculateProgress(votes, currentConfig.minVotes, nextConfig.minVotes);
        double commentsProgress = calculateProgress(comments, currentConfig.minComments, nextConfig.minComments);
        double daysProgress = calculateProgress(activeDays, currentConfig.minActiveDays, nextConfig.minActiveDays);

        // El progreso general es el mínimo de todos los requisitos
        double overallProgress = Math.min(Math.min(pointsProgress, votesProgress),
                Math.min(commentsProgress, daysProgress));

        // Valores necesarios para el siguiente nivel
        int pointsNeeded = Math.max(0, nextConfig.minPoints - spentPoints);
        long votesNeeded = Math.max(0, nextConfig.minVotes - votes);
        long commentsNeeded = Math.max(0, nextConfig.minComments - comments);
        long daysNeeded = Math.max(0, nextConfig.minActiveDays - activeDays);

        return new LevelProgress(currentLevel, nextLevel, overallProgress,
                pointsNeeded, votesNeeded, commentsNeeded, daysNeeded);
    }

    /**
     * Calcula progreso entre dos valores
     */
    private double calculateProgress(long current, long min, long max) {
        if (current >= max) return 100.0;
        if (current <= min) return 0.0;
        return ((double)(current - min) / (max - min)) * 100;
    }

    /**
     * Verifica si un usuario puede subir de nivel
     */
    public boolean canLevelUp(User user) {
        UserLevel currentLevel = user.getLevel();
        UserLevel nextLevel = currentLevel.getNextLevel();

        if (nextLevel == null) return false;

        UserLevel calculatedLevel = calculateUserLevel(user);
        return calculatedLevel.ordinal() > currentLevel.ordinal();
    }

    /**
     * Obtiene los requisitos para un nivel específico
     */
    public Map<String, Object> getLevelRequirements(UserLevel level) {
        LevelConfig config = LEVEL_CONFIG.get(level);
        Map<String, Object> requirements = new HashMap<>();

        requirements.put("level", level);
        requirements.put("displayName", config.displayName);
        requirements.put("minPoints", config.minPoints);
        requirements.put("minVotes", config.minVotes);
        requirements.put("minComments", config.minComments);
        requirements.put("minActiveDays", config.minActiveDays);
        requirements.put("description", config.description);

        return requirements;
    }

    /**
     * Obtiene estadísticas de niveles
     */
    public Map<String, Object> getLevelStatistics() {
        Map<String, Object> stats = new HashMap<>();

        for (UserLevel level : UserLevel.values()) {
            LevelConfig config = LEVEL_CONFIG.get(level);
            long userCount = userRepository.countByLevel(level);

            Map<String, Object> levelStats = new HashMap<>();
            levelStats.put("count", userCount);
            levelStats.put("config", config);

            stats.put(level.name(), levelStats);
        }

        return stats;
    }

    // ==============================================
    // CLASES INTERNAS
    // ==============================================

    /**
     * Configuración de un nivel
     */
    public static class LevelConfig {
        private final String displayName;
        private final int minPoints;           // puntos gastados
        private final long minVotes;           // votos realizados
        private final long minComments;        // comentarios publicados
        private final long minActiveDays;      // días desde registro hasta último login
        private final String description;

        public LevelConfig(String displayName, int minPoints, long minVotes,
                           long minComments, long minActiveDays, String description) {
            this.displayName = displayName;
            this.minPoints = minPoints;
            this.minVotes = minVotes;
            this.minComments = minComments;
            this.minActiveDays = minActiveDays;
            this.description = description;
        }

        // Getters
        public String getDisplayName() { return displayName; }
        public int getMinPoints() { return minPoints; }
        public long getMinVotes() { return minVotes; }
        public long getMinComments() { return minComments; }
        public long getMinActiveDays() { return minActiveDays; }
        public String getDescription() { return description; }
    }

    /**
     * Progreso hacia el siguiente nivel
     */
    public static class LevelProgress {
        private final UserLevel currentLevel;
        private final UserLevel nextLevel;
        private final double progress;
        private final int pointsNeeded;
        private final long votesNeeded;
        private final long commentsNeeded;
        private final long daysNeeded;

        public LevelProgress(UserLevel currentLevel, UserLevel nextLevel, double progress,
                             int pointsNeeded, long votesNeeded, long commentsNeeded, long daysNeeded) {
            this.currentLevel = currentLevel;
            this.nextLevel = nextLevel;
            this.progress = progress;
            this.pointsNeeded = pointsNeeded;
            this.votesNeeded = votesNeeded;
            this.commentsNeeded = commentsNeeded;
            this.daysNeeded = daysNeeded;
        }

        public UserLevel getCurrentLevel() { return currentLevel; }
        public UserLevel getNextLevel() { return nextLevel; }
        public double getProgress() { return progress; }
        public int getPointsNeeded() { return pointsNeeded; }
        public long getVotesNeeded() { return votesNeeded; }
        public long getCommentsNeeded() { return commentsNeeded; }
        public long getDaysNeeded() { return daysNeeded; }

        public boolean hasNextLevel() {
            return nextLevel != null;
        }
    }
}