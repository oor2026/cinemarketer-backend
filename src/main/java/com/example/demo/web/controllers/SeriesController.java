package com.example.demo.web.controllers;

import com.example.demo.application.dtos.SeriesFilterDto;
import com.example.demo.application.dtos.external.tmdb.*;
import com.example.demo.application.services.SeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/series")
public class SeriesController {

    private final SeriesService seriesService;

    public SeriesController(SeriesService seriesService) {
        this.seriesService = seriesService;
    }

    /**
     * Obtener series populares
     * GET /api/series/popular?page=1
     */
    @GetMapping("/popular")
    public ResponseEntity<TmdbSeriesPageResponseDto> getPopularSeries(
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String firstAirDateGte,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer page) {

        if (sortBy != null && !sortBy.isBlank()) {
            SeriesFilterDto filter = new SeriesFilterDto();

            if ("first_air_date.desc".equals(sortBy)) {
                filter.setSortBy("popularity.desc");
                int anioActual = java.time.Year.now().getValue();
                filter.setFirstAirDateGte(String.valueOf(anioActual - 1));
            } else {
                filter.setSortBy(sortBy);
                filter.setFirstAirDateGte(firstAirDateGte);
            }

            filter.setPage(page != null ? page : 1);
            TmdbSeriesPageResponseDto response = seriesService.searchSeries(filter);
            return ResponseEntity.ok(response);
        }

        TmdbSeriesPageResponseDto response = seriesService.getPopularSeries(page);
        return ResponseEntity.ok(response);
    }

    /**
     * Series al aire — equivalente a "en cartelera"
     * GET /api/series/on-the-air?page=1
     */
    @GetMapping("/on-the-air")
    public ResponseEntity<TmdbSeriesPageResponseDto> getOnTheAirSeries(
            @RequestParam(required = false) Integer page) {
        TmdbSeriesPageResponseDto response = seriesService.getOnTheAirSeries(page);
        return ResponseEntity.ok(response);
    }

    /**
     * Series que emiten hoy — equivalente más cercano a "próximos estrenos"
     * GET /api/series/airing-today?page=1
     */
    @GetMapping("/airing-today")
    public ResponseEntity<TmdbSeriesPageResponseDto> getAiringTodaySeries(
            @RequestParam(required = false) Integer page) {
        TmdbSeriesPageResponseDto response = seriesService.getAiringTodaySeries(page);
        return ResponseEntity.ok(response);
    }

    /**
     * Detalles de una serie por ID
     * GET /api/series/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TmdbSeriesDto> getSeriesDetails(@PathVariable Long id) {
        TmdbSeriesDto response = seriesService.getSeriesDetails(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Buscar series con filtros avanzados
     * GET /api/series/search?query=...&year=2024&withGenres=18&page=1
     */
    @GetMapping("/search")
    public ResponseEntity<TmdbSeriesPageResponseDto> searchSeries(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String withGenres,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Double voteAverageGte,
            @RequestParam(required = false) Double voteAverageLte,
            @RequestParam(required = false) String withCrew,
            @RequestParam(required = false) String temporadas,
            @RequestParam(required = false, defaultValue = "1") Integer page) {

        SeriesFilterDto filter = new SeriesFilterDto();
        filter.setQuery(query);
        filter.setYear(year);
        filter.setWithGenres(withGenres);
        filter.setWithOriginalLanguage(language);
        filter.setVoteAverageGte(voteAverageGte);
        filter.setVoteAverageLte(voteAverageLte);
        filter.setWithCrew(withCrew);
        filter.setTemporadas(temporadas);
        filter.setPage(page);
        filter.setSortBy(null);

        TmdbSeriesPageResponseDto response = seriesService.searchSeries(filter);
        return ResponseEntity.ok(response);
    }

    /**
     * Géneros de series
     * GET /api/series/genres
     */
    @GetMapping("/genres")
    public ResponseEntity<TmdbGenreListResponseDto> getSeriesGenres() {
        TmdbGenreListResponseDto response = seriesService.getSeriesGenres();
        return ResponseEntity.ok(response);
    }

    /**
     * Buscar personas (elenco, creadores)
     * GET /api/series/people/search?query=...
     */
    @GetMapping("/people/search")
    public ResponseEntity<TmdbPersonSearchResponseDto> searchPeople(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "1") Integer page) {
        TmdbPersonSearchResponseDto response = seriesService.searchPeople(query, page);
        return ResponseEntity.ok(response);
    }

    /**
     * Videos (tráilers) de una serie
     * GET /api/series/{id}/videos
     */
    @GetMapping("/{id}/videos")
    public ResponseEntity<TmdbVideoDto> getSeriesVideos(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "es-MX") String language) {
        TmdbVideoDto response = seriesService.getSeriesVideos(id, language);
        return ResponseEntity.ok(response);
    }

    /**
     * Series similares
     * GET /api/series/{id}/similar
     */
    @GetMapping("/{id}/similar")
    public ResponseEntity<TmdbSeriesPageResponseDto> getSimilarSeries(@PathVariable Long id) {
        TmdbSeriesPageResponseDto response = seriesService.getSimilarSeries(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Detalle de una temporada puntual, con su lista completa de episodios
     * GET /api/series/{id}/season/{seasonNumber}
     */
    @GetMapping("/{id}/season/{seasonNumber}")
    public ResponseEntity<TmdbSeasonDetailDto> getSeasonDetails(
            @PathVariable Long id,
            @PathVariable Integer seasonNumber) {
        TmdbSeasonDetailDto response = seriesService.getSeasonDetails(id, seasonNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * Proveedores de streaming
     * GET /api/series/{id}/watch-providers
     */
    @GetMapping("/{id}/watch-providers")
    public ResponseEntity<Object> getWatchProviders(@PathVariable Long id) {
        Object response = seriesService.getWatchProviders(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/credits")
    public ResponseEntity<Object> getSeriesCredits(@PathVariable Long id) {
        Object response = seriesService.getSeriesCredits(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Clasificación por edad/país — equivalente TV de "release_dates"
     * GET /api/series/{id}/content-ratings
     */
    @GetMapping("/{id}/content-ratings")
    public ResponseEntity<Object> getContentRatings(@PathVariable Long id) {
        Object response = seriesService.getContentRatings(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/person/{id}")
    public ResponseEntity<Object> getPersonDetails(@PathVariable Long id) {
        return ResponseEntity.ok(seriesService.getPersonDetails(id));
    }

    @GetMapping("/person/{id}/credits")
    public ResponseEntity<Object> getPersonSeriesCredits(@PathVariable Long id) {
        return ResponseEntity.ok(seriesService.getPersonSeriesCredits(id));
    }
}