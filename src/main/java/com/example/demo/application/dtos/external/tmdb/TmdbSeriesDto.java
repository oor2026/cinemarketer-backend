package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class TmdbSeriesDto {

    private Long id;
    private String name;  // TMDb llama "name" al título de una serie, no "title"

    @JsonProperty("original_name")
    private String originalName;

    private String overview;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("first_air_date")
    private String firstAirDate;

    @JsonProperty("last_air_date")
    private String lastAirDate;

    @JsonProperty("vote_average")
    private Double voteAverage;

    @JsonProperty("vote_count")
    private Integer voteCount;

    @JsonProperty("genre_ids")
    private List<Integer> genreIds;

    @JsonProperty("genres")
    private List<TmdbGenreDto> genres;

    @JsonProperty("number_of_seasons")
    private Integer numberOfSeasons;

    @JsonProperty("number_of_episodes")
    private Integer numberOfEpisodes;

    private List<TmdbSeasonSummaryDto> seasons;

    @JsonProperty("episode_run_time")
    private List<Integer> episodeRunTime;  // TMDb lo devuelve como lista (puede variar por temporada)

    @JsonProperty("in_production")
    private Boolean inProduction;

    private String status;
    private String tagline;

    private Double popularity;

    @JsonProperty("original_language")
    private String originalLanguage;

    @JsonProperty("adult")
    private Boolean adult;

    public LocalDate getFirstAirDateAsLocalDate() {
        if (this.firstAirDate == null || this.firstAirDate.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(this.firstAirDate, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}