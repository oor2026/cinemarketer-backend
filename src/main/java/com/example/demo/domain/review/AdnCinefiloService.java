package com.example.demo.domain.review;

import com.example.demo.application.dtos.GenreScoreDto;
import com.example.demo.domain.watchlist.Watchlist;
import com.example.demo.domain.watchlist.WatchlistRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdnCinefiloService {

    private final AdnCinefiloRepository adnCinefiloRepository;
    private final WatchlistRepository watchlistRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<GenreScoreDto> calcular(Long userId) {
        Map<String, Integer> puntos = new HashMap<>();

        for (Object[] fila : adnCinefiloRepository.calcularPesosBase(userId)) {
            String genero = (String) fila[0];
            int peso = ((Number) fila[1]).intValue();
            puntos.merge(genero, peso, Integer::sum);
        }

        for (Watchlist w : watchlistRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (w.getMovieGenres() == null) continue;
            try {
                List<String> generos = objectMapper.readValue(
                        w.getMovieGenres(), new TypeReference<List<String>>() {});
                for (String genero : generos) {
                    puntos.merge(genero, 4, Integer::sum);
                }
            } catch (Exception e) {
                // JSON corrupto o vacío en esa fila puntual — se ignora, no rompe el resto del cálculo
            }
        }

        int total = puntos.values().stream().filter(v -> v > 0).mapToInt(Integer::intValue).sum();
        if (total <= 0) return List.of();

        return puntos.entrySet().stream()
                .filter(e -> e.getValue() > 0) // géneros con saldo negativo (muchos dislikes) no se muestran
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new GenreScoreDto(
                        e.getKey(),
                        e.getValue(),
                        Math.round(e.getValue() * 1000.0 / total) / 10.0))
                .toList();
    }
}