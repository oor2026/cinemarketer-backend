package com.example.demo.application.dtos;

import java.util.List;

public record RecomendacionItemDto(
        String slug,
        String titulo,
        String poster,
        List<String> generos,
        int coincidencias,
        String mensaje
) {}
