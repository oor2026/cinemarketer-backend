package com.example.demo.application.services;

import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.domain.genre.Genre;
import com.example.demo.domain.genre.GenreRepository;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resuelve una película por su tmdbId: si ya existe en la base local la
 * devuelve tal cual; si no, la trae de TMDb y la persiste. Extraído de
 * ReviewController.voteMovie() para reusar la misma lógica en cualquier
 * flujo que necesite "esta película tiene que existir localmente" — hoy
 * votos, y a partir de este cambio también los 4 "gustos" de Mi Sala
 * (favorita, última vista en cine, no me canso de ver, no la banco).
 */
@Service
@RequiredArgsConstructor
public class MoviePersistenceService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final MovieService movieService;

    public Movie obtenerOCrearPelicula(Long tmdbId) {
        Movie movie = movieRepository.findByTmdbId(tmdbId).orElse(null);
        if (movie != null) return movie;

        TmdbMovieDto tmdbMovie = movieService.getMovieDetails(tmdbId);
        if (tmdbMovie == null) return null;

        Movie newMovie = new Movie();
        newMovie.setTmdbId(tmdbMovie.getId());
        newMovie.setTitle(tmdbMovie.getTitle());
        newMovie.setOverview(tmdbMovie.getOverview());
        newMovie.setPosterPath(tmdbMovie.getPosterPath());
        newMovie.setBackdropPath(tmdbMovie.getBackdropPath());
        newMovie.setReleaseDate(tmdbMovie.getReleaseDateAsLocalDate());
        newMovie.setVoteAverage(tmdbMovie.getVoteAverage());
        newMovie.setVoteCount(tmdbMovie.getVoteCount());
        newMovie.setPopularity(tmdbMovie.getPopularity());
        newMovie.setActive(true);

        if (tmdbMovie.getGenres() != null) {
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
            newMovie.setGenres(generos);
        }

        try {
            return movieRepository.save(newMovie);
        } catch (DataIntegrityViolationException e) {
            // Otro thread la creó primero (mismo criterio que ya tenía ReviewController)
            return movieRepository.findByTmdbId(tmdbId)
                    .orElseThrow(() -> new RuntimeException("Error concurrente al crear película"));
        }
    }
}