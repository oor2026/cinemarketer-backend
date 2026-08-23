package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoVistasStatsDto {
    private NoVistasStatsSectionDto total;
    private NoVistasStatsSectionDto peliculas;
    private NoVistasStatsSectionDto series;
    private double pctPeliculas;
    private double pctSeries;
}