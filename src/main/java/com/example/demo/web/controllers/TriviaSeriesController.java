package com.example.demo.web.controllers;

import com.example.demo.application.dtos.TriviaEstadoSeriesResponse;
import com.example.demo.application.dtos.TriviaRankingDto;
import com.example.demo.application.dtos.TriviaRespuestaRequest;
import com.example.demo.application.dtos.TriviaRespuestaSeriesResponse;
import com.example.demo.application.services.TriviaSeriesAttemptService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trivia-series")
public class TriviaSeriesController {

    private final TriviaSeriesAttemptService triviaSeriesAttemptService;
    private final UserRepository userRepository;

    public TriviaSeriesController(TriviaSeriesAttemptService triviaSeriesAttemptService, UserRepository userRepository) {
        this.triviaSeriesAttemptService = triviaSeriesAttemptService;
        this.userRepository = userRepository;
    }

    @GetMapping("/estado")
    public ResponseEntity<TriviaEstadoSeriesResponse> obtenerEstado(
            @RequestParam(required = false) String guestToken,
            @AuthenticationPrincipal UserDetails userDetails,
            jakarta.servlet.http.HttpServletRequest request) {
        if (userDetails != null) {
            User user = getUser(userDetails);
            return ResponseEntity.ok(triviaSeriesAttemptService.obtenerOCrearIntentoDeHoy(user));
        }
        if (guestToken == null || guestToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(triviaSeriesAttemptService.obtenerOCrearIntentoInvitado(guestToken, obtenerIp(request)));
    }

    @PostMapping("/responder")
    public ResponseEntity<?> responder(
            @RequestBody TriviaRespuestaRequest request,
            @RequestParam(required = false) String guestToken,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails != null) {
                User user = getUser(userDetails);
                return ResponseEntity.ok(triviaSeriesAttemptService.responder(user, request.getOpcionElegida(), request.getTiempoSegundos()));
            }
            if (guestToken == null || guestToken.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(triviaSeriesAttemptService.responderInvitado(guestToken, request.getOpcionElegida(), request.getTiempoSegundos()));
        } catch (com.example.demo.application.services.TriviaAttemptService.RespuestaDuplicadaException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/reclamar")
    public ResponseEntity<TriviaEstadoSeriesResponse> reclamar(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = getUser(userDetails);
        String guestToken = body.get("guestToken");
        return ResponseEntity.ok(triviaSeriesAttemptService.reclamarIntentoInvitado(user, guestToken));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private String obtenerIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<TriviaRankingDto>> ranking(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = null;
        if (userDetails != null) {
            userId = getUser(userDetails).getId();
        }
        return ResponseEntity.ok(triviaSeriesAttemptService.obtenerRanking(userId));
    }
}
