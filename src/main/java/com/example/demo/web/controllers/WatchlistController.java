package com.example.demo.web.controllers;

import com.example.demo.application.dtos.WatchlistDto;
import com.example.demo.application.services.MovieService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.watchlist.Watchlist;
import com.example.demo.domain.watchlist.WatchlistRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;
    private final MovieService movieService;

    public WatchlistController(WatchlistRepository watchlistRepository,
                               UserRepository userRepository,
                               MovieService movieService) {
        this.watchlistRepository = watchlistRepository;
        this.userRepository = userRepository;
        this.movieService = movieService;
    }

    // GET /api/watchlist — listar mis películas guardadas
    @GetMapping
    public ResponseEntity<List<WatchlistDto>> getMiLista(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<WatchlistDto> result = watchlistRepository
                .findByUserIdOrderByCreatedAtDesc(me.getId())
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    // POST /api/watchlist/{movieId} — guardar o quitar de mi lista (toggle)
    @PostMapping("/{movieId}")
    @Transactional
    public ResponseEntity<?> toggle(@PathVariable Long movieId,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        // Si ya existe, eliminar (quitar de lista)
        var existing = watchlistRepository.findByUserIdAndMovieId(me.getId(), movieId);
        if (existing.isPresent()) {
            watchlistRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("saved", false));
        }

        // Si no existe, guardar
        Watchlist w = new Watchlist();
        w.setUser(me);
        w.setMovieId(movieId);

        // Obtener datos de TMDB
        try {
            var tmdb = movieService.getMovieDetails(movieId);
            if (tmdb != null) {
                w.setMovieTitle(tmdb.getTitle());
                w.setMoviePosterPath(tmdb.getPosterPath());
                w.setMovieOverview(tmdb.getOverview());
                if (tmdb.getGenres() != null && !tmdb.getGenres().isEmpty()) {
                    String genresJson = tmdb.getGenres().stream()
                            .map(g -> "\"" + g.getName() + "\"")
                            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
                    w.setMovieGenres(genresJson);
                }
            }
        } catch (Exception ignored) {}

        watchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    // GET /api/watchlist/{movieId}/status — consultar si una película está guardada
    @GetMapping("/{movieId}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long movieId,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        boolean saved = watchlistRepository.existsByUserIdAndMovieId(me.getId(), movieId);
        return ResponseEntity.ok(Map.of("saved", saved));
    }

    // POST /api/watchlist/{id}/seen — marcar como vista
    @PostMapping("/{id}/seen")
    @Transactional
    public ResponseEntity<?> marcarVista(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        Watchlist w = watchlistRepository.findByIdAndUserId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Entrada no encontrada"));

        if (w.getSeenAt() != null)
            return ResponseEntity.badRequest().body(Map.of("error", "Ya marcada como vista"));

        w.setSeenAt(LocalDateTime.now());
        w.setStatus("SEEN");
        watchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // POST /api/watchlist/{id}/rate — calificar
    @PostMapping("/{id}/rate")
    @Transactional
    public ResponseEntity<?> calificar(@PathVariable Long id,
                                       @RequestBody Map<String, Integer> body,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        Watchlist w = watchlistRepository.findByIdAndUserId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Entrada no encontrada"));

        if (w.getSeenAt() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Primero marcala como vista"));

        Integer rating = body.get("rating");
        if (rating == null || rating < 1 || rating > 5)
            return ResponseEntity.badRequest().body(Map.of("error", "Rating inválido (1-5)"));

        w.setRating(rating.shortValue());
        w.setRatedAt(LocalDateTime.now());
        w.setStatus("RATED");
        watchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // DELETE /api/watchlist/{id} — eliminar de mi lista
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        Watchlist w = watchlistRepository.findByIdAndUserId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Entrada no encontrada"));
        watchlistRepository.delete(w);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // GET /api/watchlist/ids — devuelve solo los movieIds guardados
    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getMisIds(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<Long> ids = watchlistRepository.findByUserIdOrderByCreatedAtDesc(me.getId())
                .stream()
                .map(Watchlist::getMovieId)
                .toList();
        return ResponseEntity.ok(ids);
    }

    // ── helpers ──────────────────────────────────────────────
    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private WatchlistDto toDto(Watchlist w) {
        return new WatchlistDto(
                w.getId(),
                w.getMovieId(),
                w.getMovieTitle(),
                w.getMoviePosterPath(),
                w.getMovieOverview(),
                w.getStatus(),
                w.getSeenAt(),
                w.getRating(),
                w.getRatedAt(),
                w.getCreatedAt()
        );
    }
}
