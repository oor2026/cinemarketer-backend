package com.example.demo.application.dtos;

import lombok.Data;
import java.util.Map;
import java.util.HashMap;

@Data
public class MovieFilterDto {
    private String query;
    private Integer year;
    private String withGenres;        // IDs separados por coma
    private String withOriginalLanguage;
    private Double voteAverageGte;
    private Double voteAverageLte;
    private Integer withRuntimeGte;
    private Integer withRuntimeLte;
    private Integer page = 1;
    private String withCrew;

    // Método para convertir a Map para TMDb
    public Map<String, String> toParams() {
        Map<String, String> params = new HashMap<>();

        if (query != null && !query.trim().isEmpty()) {
            params.put("query", query.trim());
        }

        if (year != null) {
            params.put("primary_release_year", year.toString());
        }

        if (withGenres != null && !withGenres.trim().isEmpty()) {
            params.put("with_genres", withGenres);
        }

        if (withOriginalLanguage != null && !withOriginalLanguage.trim().isEmpty()) {
            params.put("with_original_language", withOriginalLanguage);
        }

        if (voteAverageGte != null) {
            params.put("vote_average.gte", voteAverageGte.toString());
        }

        if (voteAverageLte != null) {
            params.put("vote_average.lte", voteAverageLte.toString());
        }

        if (withRuntimeGte != null) {
            params.put("with_runtime.gte", withRuntimeGte.toString());
        }

        if (withRuntimeLte != null) {
            params.put("with_runtime.lte", withRuntimeLte.toString());
        }

        if (withCrew != null && !withCrew.trim().isEmpty()) {
            params.put("with_crew", withCrew);
        }

        params.put("page", page.toString());

        return params;
    }

    // Determina si debemos usar search o discover
    public boolean usarSearch() {
        return query != null && !query.trim().isEmpty() &&
                year == null && withGenres == null &&
                withOriginalLanguage == null && voteAverageGte == null &&
                voteAverageLte == null && withRuntimeGte == null && withRuntimeLte == null;
    }
}