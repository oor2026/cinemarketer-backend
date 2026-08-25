package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenciasStatsDto {
    private PreferenciasStatsSectionDto total;
    private PreferenciasStatsSectionDto peliculas;
    private PreferenciasStatsSectionDto series;
}