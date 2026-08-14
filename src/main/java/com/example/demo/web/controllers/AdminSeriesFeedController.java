package com.example.demo.web.controllers;

import com.example.demo.application.dtos.SeriesFeedCarruselItemDto;
import com.example.demo.application.dtos.SeriesFeedDestacadoDto;
import com.example.demo.application.services.SeriesFeedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/series-feed")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminSeriesFeedController {

    private final SeriesFeedService seriesFeedService;

    public AdminSeriesFeedController(SeriesFeedService seriesFeedService) {
        this.seriesFeedService = seriesFeedService;
    }

    @GetMapping("/destacada")
    public ResponseEntity<SeriesFeedDestacadoDto> getDestacada() {
        SeriesFeedDestacadoDto dto = seriesFeedService.getDestacadaAdmin();
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/destacada")
    public ResponseEntity<?> setDestacada(@RequestBody Map<String, Object> body,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Long seriesId = body.get("seriesId") != null
                    ? Long.valueOf(body.get("seriesId").toString())
                    : null;
            SeriesFeedDestacadoDto dto = seriesFeedService.setDestacada(seriesId, userDetails.getUsername());
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/destacada")
    public ResponseEntity<?> quitarDestacada() {
        seriesFeedService.quitarDestacada();
        return ResponseEntity.ok(Map.of("message", "Serie destacada removida"));
    }

    @GetMapping("/carrusel")
    public ResponseEntity<List<SeriesFeedCarruselItemDto>> getCarrusel() {
        return ResponseEntity.ok(seriesFeedService.getCarruselAdmin());
    }

    @PostMapping("/carrusel/serie")
    public ResponseEntity<?> agregarSerieAlCarrusel(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            seriesFeedService.agregarSerieAlCarrusel(userDetails.getUsername());
            return ResponseEntity.ok(seriesFeedService.getCarruselAdmin());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/carrusel/ranking-trivia")
    public ResponseEntity<?> agregarRankingAlCarrusel(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            seriesFeedService.agregarRankingAlCarrusel(userDetails.getUsername());
            return ResponseEntity.ok(seriesFeedService.getCarruselAdmin());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/carrusel/serie-nueva")
    public ResponseEntity<?> agregarSerieCarrusel(@RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Long seriesId = Long.valueOf(body.get("seriesId").toString());
            seriesFeedService.agregarSerieCarruselAlCarrusel(seriesId, userDetails.getUsername());
            return ResponseEntity.ok(seriesFeedService.getCarruselAdmin());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/carrusel/premio")
    public ResponseEntity<?> agregarPremioAlCarrusel(@RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        try {
            com.example.demo.domain.feed.SeriesFeedCarruselTipo tipo =
                    com.example.demo.domain.feed.SeriesFeedCarruselTipo.valueOf(body.get("tipo").toString());
            Long rewardId = Long.valueOf(body.get("rewardId").toString());
            seriesFeedService.agregarPremioAlCarrusel(tipo, rewardId, userDetails.getUsername());
            return ResponseEntity.ok(seriesFeedService.getCarruselAdmin());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/carrusel/{itemId}")
    public ResponseEntity<?> quitarDelCarrusel(@PathVariable Long itemId) {
        seriesFeedService.quitarDelCarrusel(itemId);
        return ResponseEntity.ok(seriesFeedService.getCarruselAdmin());
    }

    @PostMapping("/carrusel/{itemId}/subir")
    public ResponseEntity<?> subirEnCarrusel(@PathVariable Long itemId) {
        seriesFeedService.moverItemCarrusel(itemId, -1);
        return ResponseEntity.ok(seriesFeedService.getCarruselAdmin());
    }

    @PostMapping("/carrusel/{itemId}/bajar")
    public ResponseEntity<?> bajarEnCarrusel(@PathVariable Long itemId) {
        seriesFeedService.moverItemCarrusel(itemId, 1);
        return ResponseEntity.ok(seriesFeedService.getCarruselAdmin());
    }
}
