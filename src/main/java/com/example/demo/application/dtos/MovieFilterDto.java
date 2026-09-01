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
    private String withKeywords;      // IDs de keywords de TMDb, separados por | (OR) o , (AND)
    private String withWatchProviders; // IDs de plataformas de streaming de TMDb
    private Integer page = 1;
    private String withCrew;
    private String sortBy;
    private String releaseDateGte;
    private String releaseDateLte;    // año "hasta" — junto con releaseDateGte arma un rango real de década

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

        if (withKeywords != null && !withKeywords.trim().isEmpty()) {
            params.put("with_keywords", withKeywords);
        }

        if (withWatchProviders != null && !withWatchProviders.trim().isEmpty()) {
            params.put("with_watch_providers", withWatchProviders);
            params.put("watch_region", "AR"); // fijo — TMDb lo exige junto con with_watch_providers
        }

        if (sortBy != null && !sortBy.trim().isEmpty()) {
            params.put("sort_by", sortBy);
        }

        if (releaseDateGte != null && !releaseDateGte.trim().isEmpty()) {
            params.put("primary_release_date.gte", releaseDateGte + "-01-01");
        }

        if (releaseDateLte != null && !releaseDateLte.trim().isEmpty()) {
            params.put("primary_release_date.lte", releaseDateLte + "-12-31");
        }

        params.put("page", page.toString());

        return params;
    }

    // Determina si debemos usar search o discover
    public boolean usarSearch() {
        return query != null && !query.trim().isEmpty() &&
                year == null && withGenres == null &&
                withOriginalLanguage == null && voteAverageGte == null &&
                voteAverageLte == null && withRuntimeGte == null &&
                withRuntimeLte == null && (sortBy == null || sortBy.isBlank());
    }
}