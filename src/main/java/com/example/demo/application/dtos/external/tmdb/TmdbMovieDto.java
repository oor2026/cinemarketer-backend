package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class TmdbMovieDto {

    private Long id;
    private String title;

    @JsonProperty("original_title")
    private String originalTitle;

    private String overview;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("release_date")
    private String releaseDate;

    @JsonProperty("vote_average")
    private Double voteAverage;

    @JsonProperty("vote_count")
    private Integer voteCount;

    @JsonProperty("genre_ids")
    private List<Integer> genreIds;

    @JsonProperty("genres")
    private List<TmdbGenreDto> genres;

    @JsonProperty("runtime")
    private Integer runtime;

    private Double popularity;

    @JsonProperty("original_language")
    private String originalLanguage;

    private Boolean video;

    @JsonProperty("adult")
    private Boolean adult;

    public LocalDate getReleaseDateAsLocalDate() {
        if (this.releaseDate == null || this.releaseDate.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(this.releaseDate, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    public static class TmdbGenreDto {
        private Integer id;
        private String name;
    }
}