package com.example.demo.web.controllers;

import com.example.demo.application.dtos.external.tmdb.TmdbSeriesDto;
import com.example.demo.application.dtos.external.tmdb.TmdbVideoDto;
import com.example.demo.domain.review.VoteType;
import com.example.demo.domain.series.SeriesReviewRepository;
import com.example.demo.domain.series.SeriesComment;
import com.example.demo.domain.series.SeriesCommentRepository;
import com.example.demo.domain.series.SeriesCommentReactionRepository;
import com.example.demo.domain.series.SeriesCommentReplyRepository;
import com.example.demo.domain.comment.ReactionType;
import com.example.demo.infrastructure.external.tmdb.TvService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/series")
public class PublicSeriesController {

    private final TvService tvService;
    private final SeriesReviewRepository seriesReviewRepository;
    private final SeriesCommentRepository seriesCommentRepository;
    private final SeriesCommentReactionRepository seriesCommentReactionRepository;
    private final SeriesCommentReplyRepository seriesCommentReplyRepository;

    public PublicSeriesController(
            TvService tvService,
            SeriesReviewRepository seriesReviewRepository,
            SeriesCommentRepository seriesCommentRepository,
            SeriesCommentReactionRepository seriesCommentReactionRepository,
            SeriesCommentReplyRepository seriesCommentReplyRepository) {
        this.tvService = tvService;
        this.seriesReviewRepository = seriesReviewRepository;
        this.seriesCommentRepository = seriesCommentRepository;
        this.seriesCommentReactionRepository = seriesCommentReactionRepository;
        this.seriesCommentReplyRepository = seriesCommentReplyRepository;
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

    /**
     * Comentarios públicos (solo no-spoiler, sin necesidad de login)
     * GET /api/public/series/{id}/comments?page=0&size=10
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<Map<String, Object>> getCommentsPublic(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<SeriesComment> visibles = seriesCommentRepository.findVisibleBySeriesIdAndSpoiler(id, false);

        int desde = page * size;
        int hasta = Math.min(desde + size, visibles.size());
        List<SeriesComment> lote = desde < visibles.size()
                ? visibles.subList(desde, hasta)
                : List.of();

        List<Map<String, Object>> dtos = lote.stream().map(c -> {
            long banco = seriesCommentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.BANCO);
            long merece = seriesCommentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.MERECE_PUNTO);
            long respuestas = seriesCommentReplyRepository.countVisibleByCommentId(c.getId());

            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("userName", c.getUser().getName());
            m.put("userAvatar", c.getUser().getEffectiveAvatarUrl());
            m.put("contenido", c.getContent());
            m.put("spoiler", c.isSpoiler());
            m.put("fechaRelativa", formatRelativa(c.getCreatedAt()));
            m.put("bancoCount", banco);
            m.put("merecePuntoCount", merece);
            m.put("replyCount", respuestas);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("comentarios", dtos));
    }

    private String formatRelativa(java.time.LocalDateTime fecha) {
        if (fecha == null) return "";
        long dias = ChronoUnit.DAYS.between(fecha, java.time.LocalDateTime.now());
        if (dias < 1) return "hoy";
        if (dias == 1) return "hace 1 día";
        if (dias < 30) return "hace " + dias + " días";
        return fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}