package com.example.demo.domain.series;

import com.example.demo.application.dtos.GenreScoreDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdnCinefiloSeriesService {

    private final AdnCinefiloSeriesRepository adnCinefiloSeriesRepository;

    public List<GenreScoreDto> calcular(Long userId) {
        Map<String, Integer> puntos = new HashMap<>();
        Map<String, Long> generoIds = new HashMap<>();

        for (Object[] fila : adnCinefiloSeriesRepository.calcularPesosBase(userId)) {
            Long generoId = ((Number) fila[0]).longValue();
            String genero = (String) fila[1];
            int peso = ((Number) fila[2]).intValue();
            puntos.merge(genero, peso, Integer::sum);
            generoIds.putIfAbsent(genero, generoId);
        }

        int total = puntos.values().stream().filter(v -> v > 0).mapToInt(Integer::intValue).sum();
        if (total <= 0) return List.of();

        return puntos.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new GenreScoreDto(
                        generoIds.get(e.getKey()),
                        e.getKey(),
                        e.getValue(),
                        Math.round(e.getValue() * 1000.0 / total) / 10.0))
                .toList();
    }
}