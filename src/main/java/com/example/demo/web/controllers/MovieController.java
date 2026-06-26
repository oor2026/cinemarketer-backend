package com.example.demo.web.controllers;

import com.example.demo.application.dtos.MovieFilterDto;
import com.example.demo.application.dtos.external.tmdb.*;
import com.example.demo.application.services.MovieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    /**
     * Obtener películas populares
     * GET /api/movies/popular?page=1
     */
    @GetMapping("/popular")
    public ResponseEntity<TmdbPageResponseDto> getPopularMovies(
            @RequestParam(required = false) String withCrew,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String releaseDateGte,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer page) {

        if (sortBy != null && !sortBy.isBlank()) {
            MovieFilterDto filter = new MovieFilterDto();

            // Para "más recientes": ordenar por popularidad desc dentro del año actual
            if ("primary_release_date.desc".equals(sortBy)) {
                filter.setSortBy("popularity.desc");
                // Filtrar solo películas del año actual hacia atrás (últimos 2 años para tener volumen)
                int anioActual = java.time.Year.now().getValue();
                filter.setReleaseDateGte(String.valueOf(anioActual - 1));
            } else {
                filter.setSortBy(sortBy);
                filter.setReleaseDateGte(releaseDateGte);
            }

            filter.setPage(page != null ? page : 1);
            TmdbPageResponseDto response = movieService.searchMovies(filter);
            return ResponseEntity.ok(response);
        }

        TmdbPageResponseDto response = movieService.getPopularMovies(page);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtener películas en cartelera
     * GET /api/movies/now-playing?page=1
     */
    @GetMapping("/now-playing")
    public ResponseEntity<TmdbPageResponseDto> getNowPlayingMovies(
            @RequestParam(required = false) Integer page) {
        TmdbPageResponseDto response = movieService.getNowPlayingMovies(page);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtener próximos estrenos
     * GET /api/movies/upcoming?page=1
     */
    @GetMapping("/upcoming")
    public ResponseEntity<TmdbPageResponseDto> getUpcomingMovies(
            @RequestParam(required = false) Integer page) {
        TmdbPageResponseDto response = movieService.getUpcomingMovies(page);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtener detalles de una película por ID
     * GET /api/movies/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TmdbMovieDto> getMovieDetails(@PathVariable Long id) {
        TmdbMovieDto response = movieService.getMovieDetails(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Buscar películas con filtros avanzados
     * GET /api/movies/search?query=...&year=2024&withGenres=28&page=1
     *
     * Parámetros soportados:
     * - query: texto de búsqueda
     * - year: año de estreno
     * - withGenres: IDs de géneros separados por coma (ej: 28,12)
     * - language: código ISO de idioma (ej: es, en)
     * - voteAverageGte: puntuación mínima (ej: 7.5)
     * - voteAverageLte: puntuación máxima (ej: 9.0)
     * - withRuntimeGte: duración mínima en minutos
     * - withRuntimeLte: duración máxima en minutos
     * - page: número de página
     */
    @GetMapping("/search")
    public ResponseEntity<TmdbPageResponseDto> searchMovies(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String withGenres,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Double voteAverageGte,
            @RequestParam(required = false) Double voteAverageLte,
            @RequestParam(required = false) Integer withRuntimeGte,
            @RequestParam(required = false) Integer withRuntimeLte,
            @RequestParam(required = false) String withCrew,
            @RequestParam(required = false, defaultValue = "1") Integer page) {

        // Crear DTO con los filtros recibidos
        MovieFilterDto filter = new MovieFilterDto();
        filter.setQuery(query);
        filter.setYear(year);
        filter.setWithGenres(withGenres);
        filter.setWithOriginalLanguage(language);
        filter.setVoteAverageGte(voteAverageGte);
        filter.setVoteAverageLte(voteAverageLte);
        filter.setWithRuntimeGte(withRuntimeGte);
        filter.setWithRuntimeLte(withRuntimeLte);
        filter.setWithCrew(withCrew);
        filter.setPage(page);
        filter.setSortBy(null);

        // Llamar al servicio (que decidirá entre search y discover)
        TmdbPageResponseDto response = movieService.searchMovies(filter);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtener lista de géneros de películas
     * GET /api/movies/genres
     */
    @GetMapping("/genres")
    public ResponseEntity<TmdbGenreListResponseDto> getMovieGenres() {
        TmdbGenreListResponseDto response = movieService.getMovieGenres();
        return ResponseEntity.ok(response);
    }

    /**
     * Buscar personas (directores, actores)
     * GET /api/movies/people/search?query=Nolan
     */
    @GetMapping("/people/search")
    public ResponseEntity<TmdbPersonSearchResponseDto> searchPeople(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "1") Integer page) {
        TmdbPersonSearchResponseDto response = movieService.searchPeople(query, page);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtener videos (tráilers) de una película
     * GET /api/movies/{id}/videos
     *
     * @param id ID de la película en TMDB
     * @return TmdbVideoDto con la lista de videos (tráilers, teasers, etc.)
     */
    @GetMapping("/{id}/videos")
    public ResponseEntity<TmdbVideoDto> getMovieVideos(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "es-MX") String language) {
        TmdbVideoDto response = movieService.getMovieVideos(id, language);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtener películas similares
     * GET /api/movies/{id}/similar
     */
    @GetMapping("/{id}/similar")
    public ResponseEntity<TmdbPageResponseDto> getSimilarMovies(@PathVariable Long id) {
        TmdbPageResponseDto response = movieService.getSimilarMovies(id);
        return ResponseEntity.ok(response);
    }
}