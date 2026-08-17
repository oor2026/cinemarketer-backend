package com.example.demo.application.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class RecommendationStatsDto {
    private RecommendationStatsSectionDto total;
    private RecommendationStatsSectionDto peliculas;
    private RecommendationStatsSectionDto series;
    private double pctPeliculas;
    private double pctSeries;
}