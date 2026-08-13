package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TmdbSeasonDetailDto {

    private Long id;
    private String name;
    private String overview;

    @JsonProperty("season_number")
    private Integer seasonNumber;

    @JsonProperty("air_date")
    private String airDate;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("vote_average")
    private Double voteAverage;

    private List<TmdbEpisodeDto> episodes;
}