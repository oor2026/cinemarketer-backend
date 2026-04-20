package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TmdbPersonDto {
    private Long id;
    private String name;

    @JsonProperty("profile_path")
    private String profilePath;

    private Double popularity;

    @JsonProperty("known_for_department")
    private String knownForDepartment;
}