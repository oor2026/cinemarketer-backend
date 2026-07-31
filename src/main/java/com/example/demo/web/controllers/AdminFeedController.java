package com.example.demo.web.controllers;

import com.example.demo.application.dtos.FeedCarruselItemDto;
import com.example.demo.application.dtos.FeedDestacadoDto;
import com.example.demo.application.services.FeedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/feed")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminFeedController {

    private final FeedService feedService;

    public AdminFeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /**
     * GET /api/admin/feed/destacada
     * Devuelve la película destacada actual, o 204 si no hay ninguna configurada
     */
    @GetMapping("/destacada")
    public ResponseEntity<FeedDestacadoDto> getDestacada() {
        FeedDestacadoDto dto = feedService.getDestacadaAdmin();
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * PUT /api/admin/feed/destacada
     * Body: { "movieId": 123 }
     * Valida contra TMDb antes de guardar
     */
    @PutMapping("/destacada")
    public ResponseEntity<?> setDestacada(@RequestBody Map<String, Object> body,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Long movieId = body.get("movieId") != null
                    ? Long.valueOf(body.get("movieId").toString())
                    : null;
            FeedDestacadoDto dto = feedService.setDestacada(movieId, userDetails.getUsername());
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/admin/feed/destacada
     * Quita la destacada — el feed real oculta la sección cuando no hay ninguna
     */
    @DeleteMapping("/destacada")
    public ResponseEntity<?> quitarDestacada() {
        feedService.quitarDestacada();
        return ResponseEntity.ok(Map.of("message", "Película destacada removida"));
    }

    @GetMapping("/carrusel")
    public ResponseEntity<List<FeedCarruselItemDto>> getCarrusel() {
        return ResponseEntity.ok(feedService.getCarruselAdmin());
    }

    @PostMapping("/carrusel/pelicula")
    public ResponseEntity<?> agregarPeliculaAlCarrusel(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            feedService.agregarPeliculaAlCarrusel(userDetails.getUsername());
            return ResponseEntity.ok(feedService.getCarruselAdmin());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/carrusel/ranking-trivia")
    public ResponseEntity<?> agregarRankingAlCarrusel(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            feedService.agregarRankingAlCarrusel(userDetails.getUsername());
            return ResponseEntity.ok(feedService.getCarruselAdmin());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/carrusel/pelicula-nueva")
    public ResponseEntity<?> agregarPeliculaCarrusel(@RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Long movieId = Long.valueOf(body.get("movieId").toString());
            feedService.agregarPeliculaCarruselAlCarrusel(movieId, userDetails.getUsername());
            return ResponseEntity.ok(feedService.getCarruselAdmin());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/carrusel/premio")
    public ResponseEntity<?> agregarPremioAlCarrusel(@RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        try {
            com.example.demo.domain.feed.FeedCarruselTipo tipo =
                    com.example.demo.domain.feed.FeedCarruselTipo.valueOf(body.get("tipo").toString());
            Long rewardId = Long.valueOf(body.get("rewardId").toString());
            feedService.agregarPremioAlCarrusel(tipo, rewardId, userDetails.getUsername());
            return ResponseEntity.ok(feedService.getCarruselAdmin());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/carrusel/{itemId}")
    public ResponseEntity<?> quitarDelCarrusel(@PathVariable Long itemId) {
        feedService.quitarDelCarrusel(itemId);
        return ResponseEntity.ok(feedService.getCarruselAdmin());
    }

    @PostMapping("/carrusel/{itemId}/subir")
    public ResponseEntity<?> subirEnCarrusel(@PathVariable Long itemId) {
        feedService.moverItemCarrusel(itemId, -1);
        return ResponseEntity.ok(feedService.getCarruselAdmin());
    }

    @PostMapping("/carrusel/{itemId}/bajar")
    public ResponseEntity<?> bajarEnCarrusel(@PathVariable Long itemId) {
        feedService.moverItemCarrusel(itemId, 1);
        return ResponseEntity.ok(feedService.getCarruselAdmin());
    }
}