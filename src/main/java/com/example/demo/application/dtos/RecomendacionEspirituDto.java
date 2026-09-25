package com.example.demo.application.dtos;

import java.util.List;

public record RecomendacionEspirituDto(
        List<GenreScoreDto> top3GenerosUsuario,
        boolean huboCoincidencia,
        List<RecomendacionItemDto> recomendaciones,
        String mensajeSinVotos
) {}
