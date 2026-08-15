package com.example.demo.application.dtos;

import lombok.Data;
import java.util.Map;
import java.util.HashMap;

@Data
public class SeriesFilterDto {
    private String query;
    private Integer year;
    private String withGenres;
    private String withOriginalLanguage;
    private Double voteAverageGte;
    private Double voteAverageLte;
    private Integer page = 1;
    private String sortBy;
    private String firstAirDateGte;
    private String withCrew; // TMDb TV discover usa with_people (combina cast+crew), no with_crew como película

    // No va en toParams() — TMDb no soporta filtrar /discover/tv por
    // cantidad de temporadas de forma nativa. Se resuelve aparte, en
    // SeriesService, pidiendo el detalle de cada resultado y filtrando
    // en memoria. Valores esperados: "todos" | "1" | "2-4" | "5+"
    private String temporadas;

    public Map<String, String> toParams() {
        Map<String, String> params = new HashMap<>();

        if (query != null && !query.trim().isEmpty()) {
            params.put("query", query.trim());
        }

        if (year != null) {
            params.put("first_air_date_year", year.toString());
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

        if (sortBy != null && !sortBy.trim().isEmpty()) {
            params.put("sort_by", sortBy);
        }

        if (firstAirDateGte != null && !firstAirDateGte.trim().isEmpty()) {
            params.put("first_air_date.gte", firstAirDateGte + "-01-01");
        }

        if (withCrew != null && !withCrew.trim().isEmpty()) {
            params.put("with_people", withCrew);
        }

        params.put("page", page.toString());

        return params;
    }

    public boolean usarSearch() {
        return query != null && !query.trim().isEmpty() &&
                year == null && withGenres == null &&
                withOriginalLanguage == null && voteAverageGte == null &&
                voteAverageLte == null && (sortBy == null || sortBy.isBlank()) &&
                (withCrew == null || withCrew.isBlank()) &&
                (temporadas == null || temporadas.isBlank() || "todos".equals(temporadas));
    }
}