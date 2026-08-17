package com.example.demo.web.controllers;

import com.example.demo.application.dtos.SeriesStatsDto;
import com.example.demo.application.dtos.VoteRequest;
import com.example.demo.application.dtos.external.tmdb.TmdbSeriesDto;
import com.example.demo.application.services.SeriesService;
import com.example.demo.application.services.PointConfigService;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.review.VoteType;
import com.example.demo.domain.series.Series;
import com.example.demo.domain.series.SeriesRepository;
import com.example.demo.domain.series.SeriesReview;
import com.example.demo.domain.series.SeriesReviewRepository;
import com.example.demo.domain.series.VotoRelampagoOmitidaSerie;
import com.example.demo.domain.series.VotoRelampagoOmitidaSerieRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
public class SeriesReviewController {

    private final SeriesReviewRepository seriesReviewRepository;
    private final UserRepository userRepository;
    private final PointConfigService pointConfigService;
    private final PointTransactionService pointTransactionService;
    private final SeriesService seriesService;
    private final SeriesRepository seriesRepository;
    private final VotoRelampagoOmitidaSerieRepository votoRelampagoOmitidaSerieRepository;

    public SeriesReviewController(
            SeriesReviewRepository seriesReviewRepository,
            UserRepository userRepository,
            PointConfigService pointConfigService,
            PointTransactionService pointTransactionService,
            SeriesService seriesService,
            SeriesRepository seriesRepository,
            VotoRelampagoOmitidaSerieRepository votoRelampagoOmitidaSerieRepository
    ) {
        this.seriesReviewRepository = seriesReviewRepository;
        this.userRepository = userRepository;
        this.pointConfigService = pointConfigService;
        this.pointTransactionService = pointTransactionService;
        this.seriesService = seriesService;
        this.seriesRepository = seriesRepository;
        this.votoRelampagoOmitidaSerieRepository = votoRelampagoOmitidaSerieRepository;
    }

    @PostMapping("/series/{seriesId}")
    @Transactional
    public ResponseEntity<SeriesStatsDto> voteSeries(
            @PathVariable Long seriesId,
            @RequestBody VoteRequest voteRequest,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Optional<SeriesReview> existingVote = seriesReviewRepository
                .findByUserIdAndSeriesId(user.getId(), seriesId);

        if (existingVote.isPresent()) {
            SeriesReview existing = existingVote.get();

            VoteType newVoteType;
            try {
                newVoteType = VoteType.valueOf(voteRequest.getVoteType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }

            if (existing.getVote() == newVoteType) {
                return getSeriesStats(seriesId, userDetails);
            }

            existing.setVote(newVoteType);
            seriesReviewRepository.save(existing);

            return getSeriesStats(seriesId, userDetails);
        }

        VoteType voteType;
        try {
            voteType = VoteType.valueOf(voteRequest.getVoteType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        // Obtener o crear la serie
        Series serie = seriesRepository.findByTmdbId(seriesId).orElse(null);

        if (serie == null) {
            TmdbSeriesDto tmdbSeries = seriesService.getSeriesDetails(seriesId);

            if (tmdbSeries != null) {
                Series newSeries = new Series();
                newSeries.setTmdbId(tmdbSeries.getId());
                newSeries.setTitle(tmdbSeries.getName());
                newSeries.setOverview(tmdbSeries.getOverview());
                newSeries.setPosterPath(tmdbSeries.getPosterPath());
                newSeries.setBackdropPath(tmdbSeries.getBackdropPath());
                newSeries.setFirstAirDate(tmdbSeries.getFirstAirDateAsLocalDate());
                newSeries.setVoteAverage(tmdbSeries.getVoteAverage());
                newSeries.setVoteCount(tmdbSeries.getVoteCount());
                newSeries.setPopularity(tmdbSeries.getPopularity());
                newSeries.setActive(true);

                try {
                    serie = seriesRepository.save(newSeries);
                } catch (DataIntegrityViolationException e) {
                    serie = seriesRepository.findByTmdbId(seriesId)
                            .orElseThrow(() -> new RuntimeException("Error concurrente al crear serie"));
                }
            }
        }

        int basePoints = pointConfigService.getPoints(PointAction.VOTE_SERIES);
        int points = user.isActivePremium() ? basePoints * 2 : basePoints;

        SeriesReview review = new SeriesReview();
        review.setUser(user);
        review.setSeriesId(seriesId);
        review.setVote(voteType);
        review.setPointsAwarded(points);
        seriesReviewRepository.save(review);

        user.addPoints(points);
        userRepository.save(user);

        // Si en algún momento dijo "No la vi" en Voto Relámpago, este voto
        // la marca como superada — deja de contar para el cooldown de 20
        // días, pero el registro se conserva para analítica.
        votoRelampagoOmitidaSerieRepository.findByUserIdAndSeriesId(user.getId(), seriesId)
                .filter(o -> !o.isSupersededByVote())
                .ifPresent(o -> {
                    o.setSupersededByVote(true);
                    o.setSupersededAt(java.time.LocalDateTime.now());
                    votoRelampagoOmitidaSerieRepository.save(o);
                });

        String seriesTitle = serie != null ? serie.getTitle() : ("Serie #" + seriesId);

        pointTransactionService.registerEarned(
                user,
                PointAction.VOTE_SERIES,
                points,
                seriesId,
                "Voto en serie: " + seriesTitle
        );

        long likes = seriesReviewRepository.countBySeriesIdAndVote(seriesId, VoteType.LIKE);
        long dislikes = seriesReviewRepository.countBySeriesIdAndVote(seriesId, VoteType.DISLIKE);
        long totalVotes = likes + dislikes;

        double positivePercentage = totalVotes > 0
                ? (likes * 100.0 / totalVotes)
                : 0;

        Optional<SeriesReview> updatedVote = seriesReviewRepository
                .findByUserIdAndSeriesId(user.getId(), seriesId);

        SeriesStatsDto stats = new SeriesStatsDto(
                seriesId,
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

    @GetMapping("/series/{seriesId}/stats")
    public ResponseEntity<SeriesStatsDto> getSeriesStats(
            @PathVariable Long seriesId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = null;
        if (userDetails != null) {
            user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        }

        long likes = seriesReviewRepository.countBySeriesIdAndVote(seriesId, VoteType.LIKE);
        long dislikes = seriesReviewRepository.countBySeriesIdAndVote(seriesId, VoteType.DISLIKE);
        long totalVotes = likes + dislikes;

        double positivePercentage = totalVotes > 0
                ? (likes * 100.0 / totalVotes)
                : 0;

        boolean userVoted = false;
        String userVoteType = null;

        if (user != null) {
            Optional<SeriesReview> vote = seriesReviewRepository
                    .findByUserIdAndSeriesId(user.getId(), seriesId);

            if (vote.isPresent()) {
                userVoted = true;
                userVoteType = vote.get().getVote().name();
            }
        }

        SeriesStatsDto stats = new SeriesStatsDto(
                seriesId,
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

    /**
     * GET /api/reviews/series/voted-ids
     */
    @GetMapping("/series/voted-ids")
    public ResponseEntity<java.util.List<Long>> getVotedSeriesIds(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.ok(java.util.List.of());
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        java.util.List<Long> votadas = seriesReviewRepository
                .findByUserIdAndVoteIsNotNullOrderByCreatedAtDesc(user.getId())
                .stream().map(SeriesReview::getSeriesId).distinct().toList();

        return ResponseEntity.ok(votadas);
    }

    // POST /api/reviews/series/{seriesId}/omitir — "No la vi" en Voto Relámpago
    @PostMapping("/series/{seriesId}/omitir")
    @Transactional
    public ResponseEntity<?> omitirSerie(
            @PathVariable Long seriesId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        VotoRelampagoOmitidaSerie omitida = votoRelampagoOmitidaSerieRepository
                .findByUserIdAndSeriesId(user.getId(), seriesId)
                .orElseGet(() -> {
                    VotoRelampagoOmitidaSerie nueva = new VotoRelampagoOmitidaSerie();
                    nueva.setUser(user);
                    nueva.setSeriesId(seriesId);
                    return nueva;
                });

        omitida.setCreatedAt(java.time.LocalDateTime.now());
        omitida.setSupersededByVote(false);
        omitida.setSupersededAt(null);
        votoRelampagoOmitidaSerieRepository.save(omitida);

        return ResponseEntity.ok(java.util.Map.of("success", true));
    }

    /**
     * GET /api/reviews/series/omitidas-activas
     */
    @GetMapping("/series/omitidas-activas")
    public ResponseEntity<java.util.List<Long>> getOmitidasActivasSeries(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.ok(java.util.List.of());
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(20);
        java.util.List<Long> activas = votoRelampagoOmitidaSerieRepository
                .findActivasSeriesIds(user.getId(), cutoff);

        return ResponseEntity.ok(activas);
    }
}