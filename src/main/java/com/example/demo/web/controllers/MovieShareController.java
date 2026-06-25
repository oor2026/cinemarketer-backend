package com.example.demo.web.controllers;

import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.infrastructure.external.tmdb.TmdbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movies")
@CrossOrigin(origins = "*")
public class MovieShareController {

    private final TmdbService tmdbService;

    public MovieShareController(TmdbService tmdbService) {
        this.tmdbService = tmdbService;
    }

    @GetMapping("/og/{id}")
    public ResponseEntity<String> ogRedirect(@PathVariable Long id) {
        try {
            TmdbMovieDto movie = tmdbService.getMovieDetails(id);
            String titulo = movie.getTitle() != null ? movie.getTitle() : "Película";
            String poster = movie.getPosterPath() != null
                    ? "https://image.tmdb.org/t/p/w500" + movie.getPosterPath()
                    : "https://cinemarketer.com.ar/assets/images/isologotipo.webp";

            String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <meta property="og:title" content="%s — Cinemarketer">
                    <meta property="og:image" content="%s">
                    <meta property="og:image:width" content="500">
                    <meta property="og:image:height" content="750">
                    <meta property="og:image:type" content="image/jpeg">
                    <meta property="og:description" content="Mirá lo que opina la comunidad de Cinemarketer sobre esta película 🎬">
                    <meta property="og:url" content="https://cinemarketer.com.ar/pelicula?id=%d">
                    <meta property="og:type" content="website">
                    <meta property="og:site_name" content="Cinemarketer">
                    <meta name="twitter:card" content="summary_large_image">
                    <meta name="twitter:title" content="%s — Cinemarketer">
                    <meta name="twitter:image" content="%s">
                    <meta name="twitter:description" content="Mirá lo que opina la comunidad de Cinemarketer sobre esta película 🎬">
                    <meta http-equiv="refresh" content="0;url=https://cinemarketer.com.ar/pelicula?id=%d">
                    <script>window.location.href='https://cinemarketer.com.ar/pelicula?id=%d';</script>
                </head>
                <body></body>
                </html>
                """.formatted(titulo, poster, id, titulo, poster, id, id);

            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(html);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}