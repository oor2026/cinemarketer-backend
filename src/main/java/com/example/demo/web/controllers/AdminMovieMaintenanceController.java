package com.example.demo.web.controllers;

import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.application.services.MovieService;
import com.example.demo.domain.genre.Genre;
import com.example.demo.domain.genre.GenreRepository;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.application.dtos.external.tmdb.TmdbSeriesDto;
import com.example.demo.application.services.SeriesService;
import com.example.demo.domain.series.Series;
import com.example.demo.domain.series.SeriesRepository;
import com.example.demo.domain.recommendation.MovieRecommendation;
import com.example.demo.domain.recommendation.MovieRecommendationRepository;
import com.example.demo.domain.recommendation.SeriesRecommendation;
import com.example.demo.domain.recommendation.SeriesRecommendationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/movies")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminMovieMaintenanceController {

    private final MovieRepository movieRepository;
    private final MovieService movieService;
    private final GenreRepository genreRepository;
    private final SeriesRepository seriesRepository;
    private final SeriesService seriesService;
    private final MovieRecommendationRepository movieRecommendationRepository;
    private final SeriesRecommendationRepository seriesRecommendationRepository;
    private final com.example.demo.application.services.MoviePersistenceService moviePersistenceService;
    private final com.example.demo.application.services.SeriesPersistenceService seriesPersistenceService;

    public AdminMovieMaintenanceController(MovieRepository movieRepository,
                                           MovieService movieService,
                                           GenreRepository genreRepository,
                                           SeriesRepository seriesRepository,
                                           SeriesService seriesService,
                                           MovieRecommendationRepository movieRecommendationRepository,
                                           SeriesRecommendationRepository seriesRecommendationRepository,
                                           com.example.demo.application.services.MoviePersistenceService moviePersistenceService,
                                           com.example.demo.application.services.SeriesPersistenceService seriesPersistenceService) {
        this.movieRepository = movieRepository;
        this.movieService = movieService;
        this.genreRepository = genreRepository;
        this.seriesRepository = seriesRepository;
        this.seriesService = seriesService;
        this.movieRecommendationRepository = movieRecommendationRepository;
        this.seriesRecommendationRepository = seriesRecommendationRepository;
        this.moviePersistenceService = moviePersistenceService;
        this.seriesPersistenceService = seriesPersistenceService;
    }

    /**
     * Backfill único: completa el género de todas las películas que hoy
     * lo tienen vacío (creadas antes del fix en ReviewController que
     * resuelve géneros al votar una película nueva por primera vez).
     * POST /api/admin/movies/backfill-generos
     */
    @PostMapping("/backfill-generos")
    public ResponseEntity<?> backfillGenerosPeliculas() {
        List<Movie> sinGenero = movieRepository.findAll().stream()
                .filter(m -> m.getGenres() == null || m.getGenres().isEmpty())
                .toList();

        int procesadas = 0;
        int fallidas = 0;

        for (Movie movie : sinGenero) {
            try {
                TmdbMovieDto tmdbMovie = movieService.getMovieDetails(movie.getTmdbId());
                if (tmdbMovie != null && tmdbMovie.getGenres() != null) {
                    List<Genre> generos = new ArrayList<>();
                    for (TmdbMovieDto.TmdbGenreDto g : tmdbMovie.getGenres()) {
                        Genre genero = genreRepository.findByTmdbGenreId(g.getId())
                                .orElseGet(() -> {
                                    Genre nuevo = new Genre();
                                    nuevo.setName(g.getName());
                                    nuevo.setTmdbGenreId(g.getId());
                                    nuevo.setActive(true);
                                    return genreRepository.save(nuevo);
                                });
                        generos.add(genero);
                    }
                    movie.setGenres(generos);
                    movieRepository.save(movie);
                    procesadas++;
                }
                Thread.sleep(250);
            } catch (Exception e) {
                fallidas++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "totalEncontradas", sinGenero.size(),
                "procesadas", procesadas,
                "fallidas", fallidas
        ));
    }

    /**
     * Backfill único, equivalente a backfillGenerosPeliculas pero para series.
     * POST /api/admin/movies/backfill-generos-series
     */
    @PostMapping("/backfill-generos-series")
    public ResponseEntity<?> backfillGenerosSeries() {
        List<Series> sinGenero = seriesRepository.findAll().stream()
                .filter(s -> s.getGenres() == null || s.getGenres().isEmpty())
                .toList();

        int procesadas = 0;
        int fallidas = 0;

        for (Series serie : sinGenero) {
            try {
                TmdbSeriesDto tmdbSeries = seriesService.getSeriesDetails(serie.getTmdbId());
                if (tmdbSeries != null && tmdbSeries.getGenres() != null) {
                    List<Genre> generos = new ArrayList<>();
                    for (com.example.demo.application.dtos.external.tmdb.TmdbGenreDto g : tmdbSeries.getGenres()) {
                        Genre genero = genreRepository.findByTmdbGenreId(g.getId().intValue())
                                .orElseGet(() -> {
                                    Genre nuevo = new Genre();
                                    nuevo.setName(g.getName());
                                    nuevo.setTmdbGenreId(g.getId().intValue());
                                    nuevo.setActive(true);
                                    return genreRepository.save(nuevo);
                                });
                        generos.add(genero);
                    }
                    serie.setGenres(generos);
                    seriesRepository.save(serie);
                    procesadas++;
                }
                Thread.sleep(250);
            } catch (Exception e) {
                fallidas++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "totalEncontradas", sinGenero.size(),
                "procesadas", procesadas,
                "fallidas", fallidas
        ));
    }

    /**
     * Backfill único: persiste localmente cualquier película que tenga
     * al menos una recomendación registrada pero que todavía no exista
     * en la tabla movies (recomendaciones hechas antes del fix en
     * RecommendationController que persiste al recomendar). Cierra el
     * hueco en el ADN Cinéfilo para recomendaciones viejas — votos ya
     * quedaron cubiertos por backfill-generos.
     * POST /api/admin/movies/backfill-recomendaciones
     */
    @PostMapping("/backfill-recomendaciones")
    public ResponseEntity<?> backfillRecomendacionesPeliculas() {
        List<Long> movieIdsFaltantes = movieRecommendationRepository.findAll().stream()
                .map(MovieRecommendation::getMovieId)
                .distinct()
                .filter(id -> movieRepository.findByTmdbId(id).isEmpty())
                .toList();

        int procesadas = 0;
        int fallidas = 0;

        for (Long movieId : movieIdsFaltantes) {
            try {
                Movie movie = moviePersistenceService.obtenerOCrearPelicula(movieId);
                if (movie != null) {
                    procesadas++;
                } else {
                    fallidas++;
                }
                Thread.sleep(250);
            } catch (Exception e) {
                fallidas++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "totalEncontradas", movieIdsFaltantes.size(),
                "procesadas", procesadas,
                "fallidas", fallidas
        ));
    }

    /**
     * Backfill único, equivalente a backfillRecomendacionesPeliculas pero
     * para series.
     * POST /api/admin/movies/backfill-recomendaciones-series
     */
    @PostMapping("/backfill-recomendaciones-series")
    public ResponseEntity<?> backfillRecomendacionesSeries() {
        List<Long> seriesIdsFaltantes = seriesRecommendationRepository.findAll().stream()
                .map(SeriesRecommendation::getSeriesId)
                .distinct()
                .filter(id -> seriesRepository.findByTmdbId(id).isEmpty())
                .toList();

        int procesadas = 0;
        int fallidas = 0;

        for (Long seriesId : seriesIdsFaltantes) {
            try {
                Series serie = seriesPersistenceService.obtenerOCrearSerie(seriesId);
                if (serie != null) {
                    procesadas++;
                } else {
                    fallidas++;
                }
                Thread.sleep(250);
            } catch (Exception e) {
                fallidas++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "totalEncontradas", seriesIdsFaltantes.size(),
                "procesadas", procesadas,
                "fallidas", fallidas
        ));
    }
}