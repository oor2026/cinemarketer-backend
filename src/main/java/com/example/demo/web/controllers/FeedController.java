package com.example.demo.web.controllers;

import com.example.demo.application.services.FeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /**
     * GET /api/feed/destacada
     * Público — solo el movieId. El frontend resuelve poster/título/score
     * en vivo contra GET /movies/{id}, igual que la Ficha técnica.
     * 204 si no hay ninguna configurada (el frontend oculta la sección).
     */
    @GetMapping("/destacada")
    public ResponseEntity<?> getDestacada() {
        Long movieId = feedService.getDestacadaMovieId();
        if (movieId == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(Map.of("movieId", movieId));
    }

    @GetMapping("/carrusel")
    public ResponseEntity<List<Map<String, Object>>> getCarrusel() {
        return ResponseEntity.ok(feedService.getCarruselPublico());
    }
}