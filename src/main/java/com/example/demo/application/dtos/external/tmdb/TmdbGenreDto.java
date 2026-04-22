package com.example.demo.application.dtos.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TmdbGenreDto {
    private Long id;
    private String name;
}