package com.example.demo.web.controllers;

import com.example.demo.application.dtos.MovieStatsDto;
import com.example.demo.application.dtos.VoteRequest;
import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.application.services.MovieService;
import com.example.demo.application.services.PointConfigService;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.domain.pointconfig.PointAction;
import com.example.demo.domain.review.Review;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.review.ReviewType;
import com.example.demo.domain.review.VoteType;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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

    public ReviewController(ReviewRepository reviewRepository,
                            UserRepository userRepository,
                            PointConfigService pointConfigService,
                            PointTransactionService pointTransactionService,
                            MovieService movieService,
                            MovieRepository movieRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.pointConfigService = pointConfigService;
        this.pointTransactionService = pointTransactionService;
        this.movieService = movieService;
        this.movieRepository = movieRepository;
    }

    /**
     * Votar una película
     * POST /api/reviews/movies/{movieId}
     */
    @PostMapping("/movies/{movieId}")
    @Transactional
    public ResponseEntity<MovieStatsDto> voteMovie(
            @PathVariable Long movieId,
            @RequestBody VoteRequest voteRequest,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 1. Obtener usuario autenticado
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 2. Validar tipo de voto
        VoteType voteType;
        try {
            voteType = VoteType.valueOf(voteRequest.getVoteType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        // 3. 👉 VERIFICAR Y GUARDAR LA PELÍCULA SI ES NECESARIO
        Optional<Movie> existingMovie = movieRepository.findByTmdbId(movieId);

        if (existingMovie.isEmpty()) {
            try {

                TmdbMovieDto tmdbMovie = movieService.getMovieDetails(movieId);

                if (tmdbMovie != null) {
                    Movie newMovie = new Movie();
                    newMovie.setTmdbId(tmdbMovie.getId());
                    newMovie.setTitle(tmdbMovie.getTitle());
                    newMovie.setOverview(tmdbMovie.getOverview());
                    newMovie.setPosterPath(tmdbMovie.getPosterPath());
                    newMovie.setBackdropPath(tmdbMovie.getBackdropPath());
                    newMovie.setReleaseDate(tmdbMovie.getReleaseDateAsLocalDate());
                    newMovie.setVoteAverage(tmdbMovie.getVoteAverage());
                    newMovie.setVoteCount(tmdbMovie.getVoteCount());
                    newMovie.setPopularity(tmdbMovie.getPopularity());
                    newMovie.setActive(true);

                    movieRepository.save(newMovie);
                }
            } catch (Exception e) {
                // No impedimos el voto, pero registramos el error
            }
        } else {

        }

        // 4. Consultar puntos desde point_config
        int basePoints = pointConfigService.getPoints(PointAction.VOTE_MOVIE);
        int points = user.isActivePremium() ? basePoints * 2 : basePoints;

        // 5. Buscar si el usuario ya votó esta película
        Optional<Review> existingVote = reviewRepository
                .findByUserIdAndReviewTypeAndTargetId(user.getId(), ReviewType.MOVIE, movieId);

        if (existingVote.isPresent()) {
            // Si ya votó, solo actualizamos el tipo de voto (sin sumar puntos de nuevo)
            Review review = existingVote.get();
            review.setVote(voteType);
            reviewRepository.save(review);
        } else {
            // Si no votó, creamos nuevo voto y sumamos puntos
            Review review = new Review();
            review.setUser(user);
            review.setReviewType(ReviewType.MOVIE);
            review.setTargetId(movieId);
            review.setVote(voteType);
            review.setPointsAwarded(points);
            reviewRepository.save(review);

            user.addPoints(points);
            userRepository.save(user);

            // Registrar transacción de puntos
            String movieTitle = movieRepository.findByTmdbId(movieId)
                    .map(Movie::getTitle)
                    .orElse("Película #" + movieId);

            pointTransactionService.registerEarned(
                    user,
                    PointAction.VOTE_MOVIE,
                    points,
                    movieId,
                    "Voto en película: " + movieTitle
            );
        }

        // 6. Obtener estadísticas actualizadas
        long likes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, movieId, VoteType.LIKE);
        long dislikes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, movieId, VoteType.DISLIKE);
        long totalVotes = likes + dislikes;
        double positivePercentage = totalVotes > 0 ? (likes * 100.0 / totalVotes) : 0;

        // 7. Verificar voto del usuario para el frontend
        Optional<Review> updatedVote = reviewRepository
                .findByUserIdAndReviewTypeAndTargetId(user.getId(), ReviewType.MOVIE, movieId);
        boolean userVoted = updatedVote.isPresent();
        String userVoteType = updatedVote.map(r -> r.getVote().name()).orElse(null);

        MovieStatsDto stats = new MovieStatsDto(
                movieId,
                likes,
                dislikes,
                positivePercentage,
                totalVotes,
                userVoted,
                userVoteType
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * Obtener estadísticas de votación de una película
     * GET /api/reviews/movies/{movieId}/stats
     */
    @GetMapping("/movies/{movieId}/stats")
    public ResponseEntity<MovieStatsDto> getMovieStats(
            @PathVariable Long movieId,
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Obtener usuario autenticado (si existe)
        User user = null;
        if (userDetails != null) {
            user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        }

        // 2. Contar likes y dislikes
        long likes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, movieId, VoteType.LIKE);
        long dislikes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, movieId, VoteType.DISLIKE);
        long totalVotes = likes + dislikes;
        double positivePercentage = totalVotes > 0 ? (likes * 100.0 / totalVotes) : 0;

        // 3. Verificar si el usuario actual ya votó
        boolean userVoted = false;
        String userVoteType = null;

        if (user != null) {
            Optional<Review> userVote = reviewRepository
                    .findByUserIdAndReviewTypeAndTargetId(user.getId(), ReviewType.MOVIE, movieId);

            if (userVote.isPresent()) {
                userVoted = true;
                userVoteType = userVote.get().getVote().name();
            }
        }

        // 4. Crear respuesta
        MovieStatsDto stats = new MovieStatsDto(
                movieId,
                likes,
                dislikes,
                positivePercentage,
                totalVotes,
                userVoted,
                userVoteType
        );

        return ResponseEntity.ok(stats);
    }
}
