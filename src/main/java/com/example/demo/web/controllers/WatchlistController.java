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
    private final com.example.demo.application.services.MoviePersistenceService moviePersistenceService;

    public WatchlistController(WatchlistRepository watchlistRepository,
                               UserRepository userRepository,
                               MovieService movieService,
                               com.example.demo.application.services.MoviePersistenceService moviePersistenceService) {
        this.watchlistRepository = watchlistRepository;
        this.userRepository = userRepository;
        this.movieService = movieService;
        this.moviePersistenceService = moviePersistenceService;
    }

    // GET /api/watchlist — listar mis películas guardadas
    @GetMapping
    public ResponseEntity<List<WatchlistDto>> getMiLista(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<WatchlistDto> result = watchlistRepository
                .findByUserIdAndHiddenFalseOrderByCreatedAtDesc(me.getId())
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

        // Si ya tiene una entrada ACTIVA, ocultarla (nunca se borra) —
        // preserva el historial completo para analítica futura.
        var existing = watchlistRepository.findByUserIdAndMovieIdAndHiddenFalse(me.getId(), movieId);
        if (existing.isPresent()) {
            Watchlist w = existing.get();
            w.setHidden(true);
            watchlistRepository.save(w);
            return ResponseEntity.ok(Map.of("saved", false));
        }
        // No hay entrada activa — se crea una fila NUEVA siempre, aunque
        // ya haya habido guardados anteriores ocultos para esta misma
        // película. Así cada guardado conserva su propio motivo, sin
        // pisar el de una vez anterior.

        // Si no existe, guardar
        Watchlist w = new Watchlist();
        w.setUser(me);
        w.setMovieId(movieId);

        // Persiste la película localmente si todavía no existe — mismo
        // criterio que votos, gustos, recomendaciones y comentarios.
        // Reusa el resultado para el snapshot de esta watchlist en vez de
        // pegarle a TMDb en vivo aparte. movieGenres queda igual que
        // antes (JSON de nombres sueltos) — es un campo propio de
        // Watchlist para mostrar géneros sin JOIN, no se tocó su formato.
        try {
            var movie = moviePersistenceService.obtenerOCrearPelicula(movieId);
            if (movie != null) {
                w.setMovieTitle(movie.getTitle());
                w.setMoviePosterPath(movie.getPosterPath());
                w.setMovieOverview(movie.getOverview());
                if (movie.getGenres() != null && !movie.getGenres().isEmpty()) {
                    String genresJson = movie.getGenres().stream()
                            .map(g -> "\"" + g.getName() + "\"")
                            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
                    w.setMovieGenres(genresJson);
                }
            }
        } catch (Exception ignored) {}

        Watchlist guardada = watchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("saved", true, "id", guardada.getId()));
    }

    // GET /api/watchlist/{movieId}/status — consultar si una película está guardada
    @GetMapping("/{movieId}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long movieId,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        boolean saved = watchlistRepository.existsByUserIdAndMovieIdAndHiddenFalse(me.getId(), movieId);
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
        w.setHidden(true);
        watchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // GET /api/watchlist/ids — devuelve solo los movieIds guardados
    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getMisIds(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<Long> ids = watchlistRepository.findByUserIdAndHiddenFalseOrderByCreatedAtDesc(me.getId())
                .stream()
                .map(Watchlist::getMovieId)
                .toList();
        return ResponseEntity.ok(ids);
    }

    // PATCH /api/watchlist/{id}/motivo — opcional, se llama después del
    // guardado si el usuario elige un motivo en el modal amigable. Si
    // nunca se llama, motivo queda en null — el guardado en sí ya se
    // completó antes, esto es puro dato adicional, no bloquea nada.
    @PatchMapping("/{id}/motivo")
    public ResponseEntity<?> setMotivo(@PathVariable Long id,
                                       @RequestBody Map<String, String> body,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        Watchlist w = watchlistRepository.findById(id)
                .filter(item -> item.getUser().getId().equals(me.getId()))
                .orElseThrow(() -> new RuntimeException("No encontrada"));
        w.setMotivo(body.get("motivo"));
        watchlistRepository.save(w);
        return ResponseEntity.ok(Map.of("success", true));
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
