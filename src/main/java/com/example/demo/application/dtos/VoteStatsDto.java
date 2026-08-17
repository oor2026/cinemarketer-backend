package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteStatsDto {
    private VoteStatsSectionDto total;      // combinado, más el split abajo
    private VoteStatsSectionDto peliculas;
    private VoteStatsSectionDto series;
    private double pctPeliculas; // para la barra de proporción del tab Total
    private double pctSeries;
}