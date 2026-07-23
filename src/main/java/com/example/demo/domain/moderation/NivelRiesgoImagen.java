package com.example.demo.domain.moderation;

public enum NivelRiesgoImagen {
    BAJO,   // score de riesgo bajo — publicación directa
    GRIS,   // dudoso — a revisión solo si la cuenta es nueva
    ALTO    // score alto — siempre a revisión, sin importar antigüedad
}