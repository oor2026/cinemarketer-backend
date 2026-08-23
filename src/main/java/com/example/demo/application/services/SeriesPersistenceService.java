package com.example.demo.application.services;

import com.example.demo.application.dtos.external.tmdb.TmdbSeriesDto;
import com.example.demo.domain.genre.Genre;
import com.example.demo.domain.genre.GenreRepository;
import com.example.demo.domain.series.Series;
import com.example.demo.domain.series.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resuelve una serie por su tmdbId: si ya existe en la base local la
 * devuelve tal cual; si no, la trae de TMDb y la persiste. Espejo de
 * MoviePersistenceService — mismo criterio, mismo alcance de campos
 * (título, poster, géneros; no duplica todo lo que trae TmdbSeriesDto).
 */
@Service
@RequiredArgsConstructor
public class SeriesPersistenceService {

    private final SeriesRepository seriesRepository;
    private final GenreRepository genreRepository;
    private final SeriesService seriesService;

    public Series obtenerOCrearSerie(Long tmdbId) {
        Series serie = seriesRepository.findByTmdbId(tmdbId).orElse(null);
        if (serie != null) return serie;

        TmdbSeriesDto tmdbSeries = seriesService.getSeriesDetails(tmdbId);
        if (tmdbSeries == null) return null;

        Series nuevaSerie = new Series();
        nuevaSerie.setTmdbId(tmdbSeries.getId());
        nuevaSerie.setTitle(tmdbSeries.getName());
        nuevaSerie.setOverview(tmdbSeries.getOverview());
        nuevaSerie.setPosterPath(tmdbSeries.getPosterPath());
        nuevaSerie.setBackdropPath(tmdbSeries.getBackdropPath());
        nuevaSerie.setFirstAirDate(tmdbSeries.getFirstAirDateAsLocalDate());
        nuevaSerie.setVoteAverage(tmdbSeries.getVoteAverage());
        nuevaSerie.setVoteCount(tmdbSeries.getVoteCount());
        nuevaSerie.setPopularity(tmdbSeries.getPopularity());
        nuevaSerie.setActive(true);

        if (tmdbSeries.getGenres() != null) {
            List<Genre> generos = new ArrayList<>();
            for (var g : tmdbSeries.getGenres()) {
                // TmdbSeriesDto usa el TmdbGenreDto "top-level" (id: Long),
                // distinto del anidado TmdbMovieDto.TmdbGenreDto (id: Integer)
                // que usa Películas — GenreRepository espera Integer en los
                // dos casos, así que acá hace falta la conversión explícita.
                Integer generoTmdbId = g.getId() != null ? g.getId().intValue() : null;
                Genre genero = genreRepository.findByTmdbGenreId(generoTmdbId)
                        .orElseGet(() -> {
                            Genre nuevo = new Genre();
                            nuevo.setName(g.getName());
                            nuevo.setTmdbGenreId(generoTmdbId);
                            nuevo.setActive(true);
                            return genreRepository.save(nuevo);
                        });
                generos.add(genero);
            }
            nuevaSerie.setGenres(generos);
        }

        try {
            return seriesRepository.save(nuevaSerie);
        } catch (DataIntegrityViolationException e) {
            return seriesRepository.findByTmdbId(tmdbId)
                    .orElseThrow(() -> new RuntimeException("Error concurrente al crear serie"));
        }
    }
}