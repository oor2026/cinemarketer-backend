package com.example.demo.web.controllers;

import com.example.demo.application.dtos.MovieStatsDto;
import com.example.demo.application.dtos.VoteRequest;
import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.application.services.MovieService;
import com.example.demo.application.services.PointConfigService;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.domain.genre.Genre;
import com.example.demo.domain.genre.GenreRepository;
import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.review.*;
import java.util.List;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PointConfigService pointConfigService;
    private final PointTransactionService pointTransactionService;
    private final MovieService movieService;
    private final MovieRepository movieRepository;
    private final VotoRelampagoOmitidaRepository votoRelampagoOmitidaRepository;
    private final com.example.demo.application.services.MoviePersistenceService moviePersistenceService;

    public ReviewController(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            PointConfigService pointConfigService,
            PointTransactionService pointTransactionService,
            MovieService movieService,
            MovieRepository movieRepository,
            VotoRelampagoOmitidaRepository votoRelampagoOmitidaRepository,
            com.example.demo.application.services.MoviePersistenceService moviePersistenceService
    ) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.pointConfigService = pointConfigService;
        this.pointTransactionService = pointTransactionService;
        this.movieService = movieService;
        this.movieRepository = movieRepository;
        this.votoRelampagoOmitidaRepository = votoRelampagoOmitidaRepository;
        this.moviePersistenceService = moviePersistenceService;
    }

    @PostMapping("/movies/{movieId}")
    @Transactional
    public ResponseEntity<MovieStatsDto> voteMovie(
            @PathVariable Long movieId,
            @RequestBody VoteRequest voteRequest,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Optional<Review> existingVote = reviewRepository
                .findByUserIdAndReviewTypeAndTargetId(user.getId(), ReviewType.MOVIE, movieId);

        if (existingVote.isPresent()) {
            Review existing = existingVote.get();

            VoteType newVoteType;
            try {
                newVoteType = VoteType.valueOf(voteRequest.getVoteType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }

            // Si el voto es el mismo, no hacer nada
            if (existing.getVote() == newVoteType) {
                return getMovieStats(movieId, userDetails);
            }

            // Si el voto es diferente, solo cambiar el tipo (sin sumar puntos)
            existing.setVote(newVoteType);
            reviewRepository.save(existing);

            return getMovieStats(movieId, userDetails);
        }

        VoteType voteType;
        try {
            voteType = VoteType.valueOf(voteRequest.getVoteType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        // Obtener o crear película — extraído a MoviePersistenceService,
        // reusado también por los endpoints de "Mis gustos" en UserController.
        Movie movie = moviePersistenceService.obtenerOCrearPelicula(movieId);

        int basePoints = pointConfigService.getPoints(PointAction.VOTE_MOVIE);
        int points = user.isActivePremium() ? basePoints * 2 : basePoints;

        Review review = new Review();
        review.setUser(user);
        review.setReviewType(ReviewType.MOVIE);
        review.setTargetId(movieId);
        review.setVote(voteType);
        review.setPointsAwarded(points);
        reviewRepository.save(review);

        user.addPoints(points);
        userRepository.save(user);

        // Si en algún momento dijo "No la vi" en Voto Relámpago, este voto
        // (venga de donde venga: modal, card, o el propio Voto Relámpago)
        // lo marca como superado — deja de contar para el cooldown de 20
        // días, pero el registro se conserva para analítica.
        votoRelampagoOmitidaRepository.findByUserIdAndMovieId(user.getId(), movieId)
                .filter(o -> !o.isSupersededByVote())
                .ifPresent(o -> {
                    o.setSupersededByVote(true);
                    o.setSupersededAt(java.time.LocalDateTime.now());
                    votoRelampagoOmitidaRepository.save(o);
                });

        String movieTitle = movie != null ? movie.getTitle() : ("Película #" + movieId);

        pointTransactionService.registerEarned(
                user,
                PointAction.VOTE_MOVIE,
                points,
                movieId,
                "Voto en película: " + movieTitle
        );

        long likes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, movieId, VoteType.LIKE);

        long dislikes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, movieId, VoteType.DISLIKE);

        long totalVotes = likes + dislikes;

        double positivePercentage = totalVotes > 0
                ? (likes * 100.0 / totalVotes)
                : 0;

        Optional<Review> updatedVote = reviewRepository
                .findByUserIdAndReviewTypeAndTargetId(user.getId(), ReviewType.MOVIE, movieId);

        MovieStatsDto stats = new MovieStatsDto(
                movieId,
                likes,
                dislikes,
                positivePercentage,
                totalVotes,
                updatedVote.isPresent(),
                updatedVote.map(r -> r.getVote().name()).orElse(null),
                points
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * IDs de películas que el usuario ya votó (LIKE o DISLIKE).
     * Usado por Voto Relámpago para filtrar candidatos sin pedir
     * stats de a uno.
     * GET /api/reviews/movies/voted-ids
     */
    @GetMapping("/movies/voted-ids")
    public ResponseEntity<java.util.List<Long>> getVotedMovieIds(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.ok(java.util.List.of());
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        java.util.List<Long> votadas = reviewRepository
                .findTargetIdsByUserIdAndReviewType(user.getId(), ReviewType.MOVIE);

        return ResponseEntity.ok(votadas);
    }

    // POST /api/reviews/movies/{movieId}/omitir — "No la vi" en Voto Relámpago
    @PostMapping("/movies/{movieId}/omitir")
    @Transactional
    public ResponseEntity<?> omitirMovie(
            @PathVariable Long movieId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        VotoRelampagoOmitida omitida = votoRelampagoOmitidaRepository
                .findByUserIdAndMovieId(user.getId(), movieId)
                .orElseGet(() -> {
                    VotoRelampagoOmitida nueva = new VotoRelampagoOmitida();
                    nueva.setUser(user);
                    nueva.setMovieId(movieId);
                    return nueva;
                });

        // Si ya existía (por ejemplo, volvió a aparecer tras 20 días y la
        // omitió de nuevo), refrescamos la fecha y reseteamos el estado.
        omitida.setCreatedAt(java.time.LocalDateTime.now());
        omitida.setSupersededByVote(false);
        omitida.setSupersededAt(null);
        votoRelampagoOmitidaRepository.save(omitida);

        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * IDs de películas que hoy están bloqueadas para Voto Relámpago porque
     * el usuario dijo "No la vi" y todavía no pasaron los 20 días (y no
     * las votó después, lo cual las liberaría antes).
     * GET /api/reviews/movies/omitidas-activas
     */
    @GetMapping("/movies/omitidas-activas")
    public ResponseEntity<List<Long>> getOmitidasActivas(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.ok(List.of());
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(20);
        List<Long> activas = votoRelampagoOmitidaRepository
                .findActivasMovieIds(user.getId(), cutoff);

        return ResponseEntity.ok(activas);
    }

    @GetMapping("/movies/{movieId}/stats")
    public ResponseEntity<MovieStatsDto> getMovieStats(
            @PathVariable Long movieId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = null;
        if (userDetails != null) {
            user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        }

        long likes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, movieId, VoteType.LIKE);

        long dislikes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, movieId, VoteType.DISLIKE);

        long totalVotes = likes + dislikes;

        double positivePercentage = totalVotes > 0
                ? (likes * 100.0 / totalVotes)
                : 0;

        boolean userVoted = false;
        String userVoteType = null;

        if (user != null) {
            Optional<Review> vote = reviewRepository
                    .findByUserIdAndReviewTypeAndTargetId(user.getId(), ReviewType.MOVIE, movieId);

            if (vote.isPresent()) {
                userVoted = true;
                userVoteType = vote.get().getVote().name();
            }
        }

        MovieStatsDto stats = new MovieStatsDto(
                movieId,
                likes,
                dislikes,
                positivePercentage,
                totalVotes,
                userVoted,
                userVoteType,
                null
        );

        return ResponseEntity.ok(stats);
    }
}