package com.example.demo.application.services;

import com.example.demo.domain.comment.CommentReactionRepository;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.follow.UserFollowRepository;
import com.example.demo.domain.publication.PublicationRepository;
import com.example.demo.domain.recommendation.MovieRecommendationRepository;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class LevelCalculatorService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final UserFollowRepository userFollowRepository;
    private final MovieRecommendationRepository recommendationRepository;
    private final PublicationRepository publicationRepository;

    public LevelCalculatorService(
            UserRepository userRepository,
            ReviewRepository reviewRepository,
            CommentRepository commentRepository,
            CommentReactionRepository commentReactionRepository,
            UserFollowRepository userFollowRepository,
            MovieRecommendationRepository recommendationRepository,
            PublicationRepository publicationRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
        this.commentReactionRepository = commentReactionRepository;
        this.userFollowRepository = userFollowRepository;
        this.recommendationRepository = recommendationRepository;
        this.publicationRepository = publicationRepository;
    }

    /**
     * Calcula el nivel que le corresponde al usuario según el nuevo sistema de capas.
     * El nivel NUNCA baja — si ya tiene un nivel superior, lo mantiene.
     */
    public UserLevel calculateUserLevel(User user) {
        UserLevel current = user.getLevel();

        // Intentar subir desde el nivel actual
        if (current == UserLevel.AMATEUR && cumpleCapa1(user)) {
            return UserLevel.COLABORADOR;
        }
        if (current == UserLevel.COLABORADOR && cumpleCapa2(user)) {
            return UserLevel.CRITICO;
        }
        if (current == UserLevel.CRITICO && cumpleCapa3(user)) {
            return UserLevel.JURADO_EXPERTO;
        }

        return current;
    }

    // ── CAPA 1: Amateur → Colaborador ─────────────────────────────────────────
    private boolean cumpleCapa1(User user) {
        Long userId = user.getId();

        // Email verificado (solo cuentas tradicionales — Google se considera verificado)
        boolean emailOk = user.isEmailVerified() || user.getGoogleId() != null;
        if (!emailOk) return false;

        // Perfil completo: nombre, DNI, teléfono, avatar, provincia, localidad
        boolean perfilCompleto =
                user.getName() != null && !user.getName().isBlank() &&
                        user.getDni() != null && !user.getDni().isBlank() &&
                        user.getPhone() != null && !user.getPhone().isBlank() &&
                        user.getEffectiveAvatarUrl() != null &&
                        user.getProvincia() != null && !user.getProvincia().isBlank() &&
                        user.getLocalidad() != null && !user.getLocalidad().isBlank();
        if (!perfilCompleto) return false;

        // Bio completa (Quién soy, en Mi Sala): título y texto, los dos.
        boolean bioCompleta =
                user.getBioTitulo() != null && !user.getBioTitulo().isBlank() &&
                        user.getBioTexto() != null && !user.getBioTexto().isBlank();
        if (!bioCompleta) return false;

        // 100 películas únicas votadas
        long peliculas = reviewRepository.countDistinctMoviesVotedByUser(userId);
        if (peliculas < 100) return false;

        // 50 comentarios visibles en películas distintas
        long comentarios = commentRepository.countDistinctMoviesCommentedByUser(userId);
        if (comentarios < 50) return false;

        return true;
    }

    // ── CAPA 2: Colaborador → Crítico ─────────────────────────────────────────
    private boolean cumpleCapa2(User user) {
        Long userId = user.getId();

        // 200 películas únicas votadas
        if (reviewRepository.countDistinctMoviesVotedByUser(userId) < 200) return false;

        // 100 comentarios en películas distintas
        if (commentRepository.countDistinctMoviesCommentedByUser(userId) < 100) return false;

        // 25 usuarios seguidos
        if (userFollowRepository.countByFollowerId(userId) < 25) return false;

        // 60 días activos (desde createdAt hasta lastLoginAt)
        if (!cumpleDiasActivos(user, 60)) return false;

        // 30 recomendaciones enviadas
        if (recommendationRepository.countBySenderId(userId) < 30) return false;

        // 20 "Te banco" recibidos de usuarios diferentes
        if (commentReactionRepository.countDistinctBancoGiversForUser(userId) < 20) return false;

        // 50 publicaciones en Comunidad
        if (publicationRepository.countByUserIdAndHiddenFalse(user.getId()) < 50) return false;

        // 4.000 puntos canjeados históricos (ajustado v2.0)
        if (user.getTotalRedeemedPoints() < 4000) return false;

        return true;
    }

    // ── CAPA 3: Crítico → Jurado Experto ──────────────────────────────────────
    private boolean cumpleCapa3(User user) {
        Long userId = user.getId();

        // Debe ser Premium activo
        if (!user.isActivePremium()) return false;

        // 500 películas únicas votadas
        if (reviewRepository.countDistinctMoviesVotedByUser(userId) < 500) return false;

        // 300 comentarios en películas distintas
        if (commentRepository.countDistinctMoviesCommentedByUser(userId) < 300) return false;

        // 100 usuarios seguidos
        if (userFollowRepository.countByFollowerId(userId) < 100) return false;

        // 120 días activos
        if (!cumpleDiasActivos(user, 120)) return false;

        // 200 recomendaciones enviadas
        if (recommendationRepository.countBySenderId(userId) < 200) return false;

        // 100 "Te banco" recibidos de usuarios diferentes
        if (commentReactionRepository.countDistinctBancoGiversForUser(userId) < 100) return false;

        // 100 "Merecés un punto" recibidos
        if (commentReactionRepository.countMerecePuntosRecibidosByUser(userId) < 100) return false;

        // 100 seguidores ganados
        if (userFollowRepository.countByFollowingId(userId) < 100) return false;

        // 200 publicaciones en Comunidad
        if (publicationRepository.countByUserIdAndHiddenFalse(user.getId()) < 200) return false;

        // 20.000 puntos canjeados históricos (ajustado v2.0)
        if (user.getTotalRedeemedPoints() < 20000) return false;

        return true;
    }

    // ── Días activos ───────────────────────────────────────────────────────────
    private boolean cumpleDiasActivos(User user, long minDias) {
        if (user.getCreatedAt() == null || user.getLastLoginAt() == null) return false;
        long dias = ChronoUnit.DAYS.between(
                user.getCreatedAt().toLocalDate(),
                user.getLastLoginAt().toLocalDate()
        );
        return dias >= minDias;
    }

    public double getProgressToNextLevel(User user) {
        UserLevel current = user.getLevel();
        if (current == UserLevel.JURADO_EXPERTO) return 100.0;

        Long userId = user.getId();

        if (current == UserLevel.AMATEUR) {
            long peliculas = reviewRepository.countDistinctMoviesVotedByUser(userId);
            long comentarios = commentRepository.countDistinctMoviesCommentedByUser(userId);
            double pPeliculas = Math.min(100.0, (peliculas / 100.0) * 100);
            double pComentarios = Math.min(100.0, (comentarios / 50.0) * 100);
            boolean bioCompleta = user.getBioTitulo() != null && !user.getBioTitulo().isBlank()
                    && user.getBioTexto() != null && !user.getBioTexto().isBlank();
            double pBio = bioCompleta ? 100.0 : 0.0;
            return Math.min(Math.min(pPeliculas, pComentarios), pBio);
        }

        if (current == UserLevel.COLABORADOR) {
            long peliculas = reviewRepository.countDistinctMoviesVotedByUser(userId);
            long comentarios = commentRepository.countDistinctMoviesCommentedByUser(userId);
            long publicaciones = publicationRepository.countByUserIdAndHiddenFalse(userId);
            double pPeliculas = Math.min(100.0, (peliculas / 200.0) * 100);
            double pComentarios = Math.min(100.0, (comentarios / 100.0) * 100);
            double pPublicaciones = Math.min(100.0, (publicaciones / 50.0) * 100);
            double pPuntos = Math.min(100.0, (user.getTotalRedeemedPoints() / 4000.0) * 100);
            return Math.min(Math.min(Math.min(pPeliculas, pComentarios), pPublicaciones), pPuntos);
        }

        if (current == UserLevel.CRITICO) {
            long peliculas = reviewRepository.countDistinctMoviesVotedByUser(userId);
            long publicaciones = publicationRepository.countByUserIdAndHiddenFalse(userId);
            double pPeliculas = Math.min(100.0, (peliculas / 500.0) * 100);
            double pPublicaciones = Math.min(100.0, (publicaciones / 200.0) * 100);
            double pPuntos = Math.min(100.0, (user.getTotalRedeemedPoints() / 20000.0) * 100);
            return Math.min(Math.min(pPeliculas, pPublicaciones), pPuntos);
        }

        return 0.0;
    }

    public boolean canLevelUp(User user) {
        return calculateUserLevel(user).ordinal() > user.getLevel().ordinal();
    }

    public int getPointsToNextLevel(User user) {
        UserLevel current = user.getLevel();
        if (current == UserLevel.AMATEUR) return 0;
        if (current == UserLevel.COLABORADOR) return Math.max(0, 4000 - user.getTotalRedeemedPoints());
        if (current == UserLevel.CRITICO) return Math.max(0, 20000 - user.getTotalRedeemedPoints());
        return 0;
    }

    public Map<String, Long> getLevelStatistics() {
        Map<String, Long> stats = new HashMap<>();
        for (UserLevel level : UserLevel.values()) {
            stats.put(level.name(), userRepository.countByLevel(level));
        }
        return stats;
    }
}