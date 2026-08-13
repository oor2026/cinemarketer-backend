package com.example.demo.web.controllers;

import com.example.demo.application.dtos.external.tmdb.TmdbSeriesDto;
import com.example.demo.application.dtos.external.tmdb.TmdbVideoDto;
import com.example.demo.domain.review.VoteType;
import com.example.demo.domain.series.SeriesReviewRepository;
import com.example.demo.infrastructure.external.tmdb.TvService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/series")
public class PublicSeriesController {

    private final TvService tvService;
    private final SeriesReviewRepository seriesReviewRepository;

    public PublicSeriesController(
            TvService tvService,
            SeriesReviewRepository seriesReviewRepository) {
        this.tvService = tvService;
        this.seriesReviewRepository = seriesReviewRepository;
    }

    /**
     * Datos de la serie desde TMDb
     * GET /api/public/series/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TmdbSeriesDto> getSeriesPublic(@PathVariable Long id) {
        try {
            TmdbSeriesDto serie = tvService.getSeriesDetails(id);
            return ResponseEntity.ok(serie);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Trailer de la serie
     * GET /api/public/series/{id}/trailer
     */
    @GetMapping("/{id}/trailer")
    public ResponseEntity<Map<String, String>> getTrailerPublic(@PathVariable Long id) {
        String[] languages = {"es-MX", "es-ES", "en-US"};
        for (String lang : languages) {
            try {
                TmdbVideoDto videos = tvService.getSeriesVideos(id, lang);
                if (videos != null && videos.getResults() != null) {
                    TmdbVideoDto.VideoResult trailer = videos.getResults().stream()
                            .filter(v -> "Trailer".equalsIgnoreCase(v.getType())
                                    && "YouTube".equalsIgnoreCase(v.getSite()))
                            .findFirst()
                            .orElse(null);
                    if (trailer != null) {
                        return ResponseEntity.ok(Map.of("youtubeKey", trailer.getKey()));
                    }
                }
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of());
    }

    /**
     * Stats de votos (likes/dislikes) de la comunidad
     * GET /api/public/series/{id}/stats
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getStatsPublic(@PathVariable Long id) {
        long likes = seriesReviewRepository.countBySeriesIdAndVote(id, VoteType.LIKE);
        long dislikes = seriesReviewRepository.countBySeriesIdAndVote(id, VoteType.DISLIKE);
        long total = likes + dislikes;
        double pct = total > 0 ? (likes * 100.0 / total) : 0;

        return ResponseEntity.ok(Map.of(
                "likes", likes,
                "dislikes", dislikes,
                "totalVotes", total,
                "positivePercentage", pct
        ));
    }

    // PENDIENTE: GET /{id}/comments — queda para cuando armemos SeriesComment +
    // SeriesCommentReaction + SeriesCommentReply + moderación como su propio
    // bloque de trabajo aparte. Comment.java no es genérico (tiene columna
    // movie_id fija, sin targetId/type como Review), así que Series necesita
    // su ecosistema de comentarios completo, calcado 1 a 1 del de Comment.
}