package com.example.demo.application.dtos;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WatchlistStatsDto {
    private WatchlistStatsSectionDto total;
    private WatchlistStatsSectionDto peliculas;
    private WatchlistStatsSectionDto series;
    private double pctPeliculas;
    private double pctSeries;
}