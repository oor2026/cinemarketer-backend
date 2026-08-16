package com.example.demo.domain.trivia;

public enum TriviaTipoPreguntaSeries {
    QUIEN_ES,           // cast agregado de la serie completa
    SERIE,              // adivina la serie (poster/sinopsis)
    QUIEN_ES_TEMPORADA, // cast + guest stars de una temporada puntual
    TEMPORADA_STILL     // ¿de qué temporada es este still?
}
