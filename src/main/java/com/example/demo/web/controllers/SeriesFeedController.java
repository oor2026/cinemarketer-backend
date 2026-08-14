package com.example.demo.web.controllers;

import com.example.demo.application.services.SeriesFeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/series-feed")
public class SeriesFeedController {

    private final SeriesFeedService seriesFeedService;

    public SeriesFeedController(SeriesFeedService seriesFeedService) {
        this.seriesFeedService = seriesFeedService;
    }

    @GetMapping("/destacada")
    public ResponseEntity<?> getDestacada() {
        Long seriesId = seriesFeedService.getDestacadaSeriesId();
        if (seriesId == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(Map.of("seriesId", seriesId));
    }

    @GetMapping("/carrusel")
    public ResponseEntity<List<Map<String, Object>>> getCarrusel() {
        return ResponseEntity.ok(seriesFeedService.getCarruselPublico());
    }
}
