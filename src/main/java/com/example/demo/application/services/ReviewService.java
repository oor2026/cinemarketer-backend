package com.example.demo.application.services;

import com.example.demo.domain.review.Review;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.review.ReviewType;
import com.example.demo.domain.review.VoteType;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.domain.pointconfig.PointAction;

import java.util.Optional;

/**
 * Servicio para gestionar reseñas y votaciones
 package com.example.demo.application.services;

 import com.example.demo.domain.review.Review;
 import com.example.demo.domain.review.ReviewRepository;
 import com.example.demo.domain.review.ReviewType;
 import com.example.demo.domain.review.VoteType;
 import com.example.demo.domain.user.User;
 import com.example.demo.domain.user.UserLevel;
 import com.example.demo.domain.user.UserRepository;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 import com.example.demo.domain.pointconfig.PointAction;

 import java.util.Optional;

 /**
 * Servicio para gestionar reseñas y votaciones
 * Integra la lógica de niveles con la actividad de votación
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PointTransactionService pointTransactionService;
    private final LevelCalculatorService levelCalculatorService;
    private final UserService userService;

    public ReviewService(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            PointTransactionService pointTransactionService,
            LevelCalculatorService levelCalculatorService,
            UserService userService) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.pointTransactionService = pointTransactionService;
        this.levelCalculatorService = levelCalculatorService;
        this.userService = userService;
    }

    // ==============================================
    // VOTACIONES
    // ==============================================

    /**
     * Vota una película (o cambia un voto existente)
     */
    @Transactional
    public Review voteMovie(User user, Long movieId, VoteType voteType, int pointsAwarded) {

        // Buscar si ya existe un voto
        Optional<Review> existingVote = reviewRepository
                .findByUserIdAndReviewTypeAndTargetId(user.getId(), ReviewType.MOVIE, movieId);

        Review review;
        boolean isNewVote;

        if (existingVote.isPresent()) {
            // Actualizar voto existente
            review = existingVote.get();
            VoteType oldVote = review.getVote();
            review.setVote(voteType);
            isNewVote = false;

            // Nota: No se suman puntos por cambiar de opinión
        } else {
            // Crear nuevo voto
            review = new Review();
            review.setUser(user);
            review.setReviewType(ReviewType.MOVIE);
            review.setTargetId(movieId);
            review.setVote(voteType);
            review.setPointsAwarded(pointsAwarded);
            isNewVote = true;
        }

        Review savedReview = reviewRepository.save(review);

        // Si es nuevo voto, sumar puntos y registrar transacción
        if (isNewVote) {
            // Los puntos del voto son fijos según FREE/PREMIUM
            int votePoints = user.isActivePremium() ? 40 : 20;

            // Sumar a puntos ACUMULADOS (no disponibles aún)
            user.addAccumulatedPoints(votePoints);
            userRepository.save(user);

            // Registrar transacción de puntos
            pointTransactionService.registerEarned(
                    user,
                    com.example.demo.domain.pointconfig.PointAction.VOTE_MOVIE,
                    votePoints,
                    movieId,
                    "Voto en película #" + movieId
            );

            // Verificar si el usuario debe subir de nivel
            checkAndUpdateLevel(user);
        }

        return savedReview;
    }

    /**
     * Elimina un voto
     */
    @Transactional
    public void deleteVote(User user, Long movieId) {
        Optional<Review> existingVote = reviewRepository
                .findByUserIdAndReviewTypeAndTargetId(user.getId(), ReviewType.MOVIE, movieId);

        if (existingVote.isPresent()) {
            Review review = existingVote.get();

            // Eliminar el voto — no se revierten puntos acumulados
            // (los puntos acumulados se liberan el 1° del mes y ya no son reversibles)
            reviewRepository.delete(review);

            // Verificar nivel (aunque no bajamos automáticamente)
            // checkAndUpdateLevel(user); // Opcional, comentado por ahora
        }
    }

    // ==============================================
    // COMENTARIOS
    // ==============================================

    /**
     * Registra un comentario en una película
     */
    @Transactional
    public Review addComment(User user, Long movieId, String comment, int pointsAwarded) {

        Review review = new Review();
        review.setUser(user);
        review.setReviewType(ReviewType.MOVIE);
        review.setTargetId(movieId);
        review.setComment(comment);
        review.setPointsAwarded(pointsAwarded);

        Review savedReview = reviewRepository.save(review);

        // Sumar puntos al usuario
        user.addPoints(pointsAwarded);
        userRepository.save(user);

        // Registrar transacción de puntos
        pointTransactionService.registerEarned(
                user,
                PointAction.COMMENT_MOVIE,
                pointsAwarded,
                movieId,
                "Comentario en película #" + movieId
        );

        // Verificar si el usuario debe subir de nivel
        checkAndUpdateLevel(user);

        return savedReview;
    }

    /**
     * Actualiza un comentario existente
     */
    @Transactional
    public Review updateComment(Long reviewId, String newComment) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        review.setComment(newComment);
        return reviewRepository.save(review);
    }

    /**
     * Elimina un comentario
     */
    @Transactional
    public void deleteComment(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        User user = review.getUser();
        int pointsToSubtract = review.getPointsAwarded();

        reviewRepository.delete(review);

        // Restar puntos
        user.subtractPoints(pointsToSubtract);
        userRepository.save(user);

        // Verificar nivel (opcional)
        // checkAndUpdateLevel(user);
    }

    // ==============================================
    // MÉTODOS DE CONSULTA
    // ==============================================

    /**
     * Obtiene el voto de un usuario para una película
     */
    public Optional<Review> getUserVoteForMovie(User user, Long movieId) {
        return reviewRepository.findByUserIdAndReviewTypeAndTargetId(
                user.getId(), ReviewType.MOVIE, movieId);
    }

    /**
     * Cuenta los votos de un usuario en un período
     */
    public long countUserVotesInPeriod(User user, java.time.LocalDateTime start,
                                       java.time.LocalDateTime end) {
        return reviewRepository.countByUserIdAndCreatedAtBetween(
                user.getId(), start, end);
    }

    /**
     * Cuenta los comentarios de un usuario en un período
     */
    public long countUserCommentsInPeriod(User user, java.time.LocalDateTime start,
                                          java.time.LocalDateTime end) {
        return reviewRepository.countByUserIdAndCommentIsNotNullAndCreatedAtBetween(
                user.getId(), start, end);
    }

    // ==============================================
    // MÉTODO PRIVADO PARA VERIFICAR NIVEL
    // ==============================================

    /**
     * Verifica si el usuario debe cambiar de nivel y lo actualiza si es necesario
     */
    private void checkAndUpdateLevel(User user) {
        if (user.isSuspended() || !user.isActive()) {
            return;
        }

        UserLevel oldLevel = user.getLevel();
        boolean levelChanged = user.updateLevelBasedOnPoints();

        if (levelChanged) {
            userService.updateUserLevel(user.getId(), user.getLevel());

        }
    }
}