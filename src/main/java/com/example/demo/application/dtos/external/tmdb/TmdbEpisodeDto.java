package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TmdbEpisodeDto {

    private Long id;
    private String name;
    private String overview;

    @JsonProperty("episode_number")
    private Integer episodeNumber;

    @JsonProperty("season_number")
    private Integer seasonNumber;

    @JsonProperty("air_date")
    private String airDate;

    private Integer runtime;

    @JsonProperty("still_path")
    private String stillPath;

    @JsonProperty("vote_average")
    private Double voteAverage;
}