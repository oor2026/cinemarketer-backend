package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TmdbGuestStarDto {
    private Long id;
    private String name;
    private String character;

    @JsonProperty("profile_path")
    private String profilePath;
}
