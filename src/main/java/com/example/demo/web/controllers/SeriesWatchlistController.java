package com.example.demo.web.controllers;

import com.example.demo.application.dtos.SeriesWatchlistDto;
import com.example.demo.application.services.SeriesService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.watchlist.SeriesWatchlist;
import com.example.demo.domain.watchlist.SeriesWatchlistRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/series-watchlist")
public class SeriesWatchlistController {

    private final SeriesWatchlistRepository seriesWatchlistRepository;
    private final UserRepository userRepository;
    private final SeriesService seriesService;

    public SeriesWatchlistController(SeriesWatchlistRepository seriesWatchlistRepository,
                                     UserRepository userRepository,
                                     SeriesService seriesService) {
        this.seriesWatchlistRepository = seriesWatchlistRepository;
        this.userRepository = userRepository;
        this.seriesService = seriesService;
    }

    @GetMapping
    public ResponseEntity<List<SeriesWatchlistDto>> getMiLista(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<SeriesWatchlistDto> result = seriesWatchlistRepository
                .findByUserIdOrderByCreatedAtDesc(me.getId())
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{seriesId}")
    @Transactional
    public ResponseEntity<?> toggle(@PathVariable Long seriesId,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        var existing = seriesWatchlistRepository.findByUserIdAndSeriesId(me.getId(), seriesId);
        if (existing.isPresent()) {
            seriesWatchlistRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("saved", false));
        }

        SeriesWatchlist w = new SeriesWatchlist();
        w.setUser(me);
        w.setSeriesId(seriesId);

        try {
            var tmdb = seriesService.getSeriesDetails(seriesId);
            if (tmdb != null) {
                w.setSeriesTitle(tmdb.getName());
                w.setSeriesPosterPath(tmdb.getPosterPath());
                w.setSeriesOverview(tmdb.getOverview());
                if (tmdb.getGenres() != null && !tmdb.getGenres().isEmpty()) {
                    String genresJson = tmdb.getGenres().stream()
                            .map(g -> "\"" + g.getName() + "\"")
                            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
                    w.setSeriesGenres(genresJson);
                }
            }
        } catch (Exception ignored) {}

        seriesWatchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    @GetMapping("/{seriesId}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long seriesId,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        boolean saved = seriesWatchlistRepository.existsByUserIdAndSeriesId(me.getId(), seriesId);
        return ResponseEntity.ok(Map.of("saved", saved));
    }

    @PostMapping("/{id}/seen")
    @Transactional
    public ResponseEntity<?> marcarVista(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        SeriesWatchlist w = seriesWatchlistRepository.findByIdAndUserId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Entrada no encontrada"));

        if (w.getSeenAt() != null)
            return ResponseEntity.badRequest().body(Map.of("error", "Ya marcada como vista"));

        w.setSeenAt(LocalDateTime.now());
        w.setStatus("SEEN");
        seriesWatchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/rate")
    @Transactional
    public ResponseEntity<?> calificar(@PathVariable Long id,
                                       @RequestBody Map<String, Integer> body,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        SeriesWatchlist w = seriesWatchlistRepository.findByIdAndUserId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Entrada no encontrada"));

        if (w.getSeenAt() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Primero marcala como vista"));

        Integer rating = body.get("rating");
        if (rating == null || rating < 1 || rating > 5)
            return ResponseEntity.badRequest().body(Map.of("error", "Rating inválido (1-5)"));

        w.setRating(rating.shortValue());
        w.setRatedAt(LocalDateTime.now());
        w.setStatus("RATED");
        seriesWatchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        SeriesWatchlist w = seriesWatchlistRepository.findByIdAndUserId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Entrada no encontrada"));
        seriesWatchlistRepository.delete(w);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getMisIds(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<Long> ids = seriesWatchlistRepository.findByUserIdOrderByCreatedAtDesc(me.getId())
                .stream()
                .map(SeriesWatchlist::getSeriesId)
                .toList();
        return ResponseEntity.ok(ids);
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private SeriesWatchlistDto toDto(SeriesWatchlist w) {
        return new SeriesWatchlistDto(
                w.getId(),
                w.getSeriesId(),
                w.getSeriesTitle(),
                w.getSeriesPosterPath(),
                w.getSeriesOverview(),
                w.getStatus(),
                w.getSeenAt(),
                w.getRating(),
                w.getRatedAt(),
                w.getCreatedAt()
        );
    }
}