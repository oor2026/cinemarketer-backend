package com.example.demo.web.controllers;

import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.application.dtos.external.tmdb.TmdbVideoDto;
import com.example.demo.domain.comment.Comment;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.review.ReviewType;
import com.example.demo.domain.review.VoteType;
import com.example.demo.infrastructure.external.tmdb.TmdbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/movies")
public class PublicMovieController {

    private final TmdbService tmdbService;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;

    public PublicMovieController(
            TmdbService tmdbService,
            ReviewRepository reviewRepository,
            CommentRepository commentRepository) {
        this.tmdbService = tmdbService;
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * Datos de la película desde TMDb
     * GET /api/public/movies/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TmdbMovieDto> getMoviePublic(@PathVariable Long id) {
        try {
            TmdbMovieDto movie = tmdbService.getMovieDetails(id);
            return ResponseEntity.ok(movie);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Trailer de la película
     * GET /api/public/movies/{id}/trailer
     */
    @GetMapping("/{id}/trailer")
    public ResponseEntity<Map<String, String>> getTrailerPublic(@PathVariable Long id) {
        String[] languages = {"es-MX", "es-ES", "en-US"};
        for (String lang : languages) {
            try {
                TmdbVideoDto videos = tmdbService.getMovieVideos(id, lang);
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
     * GET /api/public/movies/{id}/stats
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getStatsPublic(@PathVariable Long id) {
        long likes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, id, VoteType.LIKE);
        long dislikes = reviewRepository.countByReviewTypeAndTargetIdAndVote(
                ReviewType.MOVIE, id, VoteType.DISLIKE);
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
     * Comentarios públicos no-spoiler
     * GET /api/public/movies/{id}/comments?page=0&size=10
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<Map<String, Object>> getCommentsPublic(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<Comment> todos = commentRepository.findVisibleByMovieIdAndSpoiler(id, false);

        int from = Math.min(page * size, todos.size());
        int to   = Math.min(from + size, todos.size());
        List<Comment> pagina = todos.subList(from, to);

        List<Map<String, Object>> result = pagina.stream().map(c -> Map.<String, Object>of(
                "id", c.getId(),
                "contenido", c.getContent() != null ? c.getContent() : "",
                "userName", c.getUser() != null ? c.getUser().getName() : "Usuario",
                "userAvatar", c.getUser() != null && c.getUser().getAvatarUrl() != null
                        ? c.getUser().getAvatarUrl() : "",
                "bancoCount", 0,
                "merecePuntoCount", 0,
                "replyCount", 0,
                "spoiler", c.isSpoiler(),
                "createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""
        )).toList();

        return ResponseEntity.ok(Map.of(
                "content", result,
                "totalElements", todos.size(),
                "page", page,
                "size", size
        ));
    }
}