package com.example.demo.web.controllers;

import com.example.demo.domain.nointeresa.NoMeInteresa;
import com.example.demo.domain.nointeresa.NoMeInteresaRepository;
import com.example.demo.domain.nointeresa.NoMeInteresaSerie;
import com.example.demo.domain.nointeresa.NoMeInteresaSerieRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class NoMeInteresaController {

    private final NoMeInteresaRepository noMeInteresaRepository;
    private final NoMeInteresaSerieRepository noMeInteresaSerieRepository;
    private final UserRepository userRepository;

    public NoMeInteresaController(NoMeInteresaRepository noMeInteresaRepository,
                                  NoMeInteresaSerieRepository noMeInteresaSerieRepository,
                                  UserRepository userRepository) {
        this.noMeInteresaRepository = noMeInteresaRepository;
        this.noMeInteresaSerieRepository = noMeInteresaSerieRepository;
        this.userRepository = userRepository;
    }

    // POST /api/movies/{movieId}/no-me-interesa
    @PostMapping("/api/movies/{movieId}/no-me-interesa")
    @Transactional
    public ResponseEntity<?> marcarPelicula(@PathVariable Long movieId,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        // Idempotente: si ya estaba marcada, no duplica ni falla.
        if (!noMeInteresaRepository.existsByUserIdAndMovieId(me.getId(), movieId)) {
            NoMeInteresa n = new NoMeInteresa();
            n.setUser(me);
            n.setMovieId(movieId);
            noMeInteresaRepository.save(n);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // POST /api/series/{seriesId}/no-me-interesa
    @PostMapping("/api/series/{seriesId}/no-me-interesa")
    @Transactional
    public ResponseEntity<?> marcarSerie(@PathVariable Long seriesId,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        if (!noMeInteresaSerieRepository.existsByUserIdAndSeriesId(me.getId(), seriesId)) {
            NoMeInteresaSerie n = new NoMeInteresaSerie();
            n.setUser(me);
            n.setSeriesId(seriesId);
            noMeInteresaSerieRepository.save(n);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // GET /api/movies/no-me-interesa/ids — el frontend los pide una vez
    // al cargar el feed y filtra las tarjetas en el cliente, para no
    // tener que meter contexto de usuario en los endpoints públicos de
    // TMDb (MovieController es un proxy sin auth, no corresponde tocarlo).
    @GetMapping("/api/movies/no-me-interesa/ids")
    public ResponseEntity<java.util.List<Long>> getMisIdsPeliculas(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        return ResponseEntity.ok(noMeInteresaRepository.findMovieIdsByUserId(me.getId()));
    }

    @GetMapping("/api/series/no-me-interesa/ids")
    public ResponseEntity<java.util.List<Long>> getMisIdsSeries(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        return ResponseEntity.ok(noMeInteresaSerieRepository.findSeriesIdsByUserId(me.getId()));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}