package com.example.demo.web.controllers;

import com.example.demo.application.dtos.TriviaEstadoResponse;
import com.example.demo.application.dtos.TriviaRankingDto;
import com.example.demo.application.dtos.TriviaRespuestaRequest;
import com.example.demo.application.dtos.TriviaRespuestaResponse;
import com.example.demo.application.services.TriviaAttemptService;
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
@RequestMapping("/api/trivia")
public class TriviaController {

    private final TriviaAttemptService triviaAttemptService;
    private final UserRepository userRepository;

    public TriviaController(TriviaAttemptService triviaAttemptService, UserRepository userRepository) {
        this.triviaAttemptService = triviaAttemptService;
        this.userRepository = userRepository;
    }

    /**
     * Inicia el intento de hoy (o lo retoma si ya estaba en curso en otro
     * dispositivo, o devuelve el resultado si ya se jugó hoy).
     * GET /api/trivia/estado
     */
    @GetMapping("/estado")
    public ResponseEntity<TriviaEstadoResponse> obtenerEstado(
            @RequestParam(required = false) String guestToken,
            @AuthenticationPrincipal UserDetails userDetails,
            jakarta.servlet.http.HttpServletRequest request) {
        if (userDetails != null) {
            User user = getUser(userDetails);
            return ResponseEntity.ok(triviaAttemptService.obtenerOCrearIntentoDeHoy(user));
        }
        if (guestToken == null || guestToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(triviaAttemptService.obtenerOCrearIntentoInvitado(guestToken, obtenerIp(request)));
    }

    /**
     * POST /api/trivia/responder
     */
    @PostMapping("/responder")
    public ResponseEntity<?> responder(
            @RequestBody TriviaRespuestaRequest request,
            @RequestParam(required = false) String guestToken,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails != null) {
                User user = getUser(userDetails);
                return ResponseEntity.ok(triviaAttemptService.responder(user, request.getOpcionElegida(), request.getTiempoSegundos()));
            }
            if (guestToken == null || guestToken.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(triviaAttemptService.responderInvitado(guestToken, request.getOpcionElegida(), request.getTiempoSegundos()));
        } catch (TriviaAttemptService.RespuestaDuplicadaException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Se llama automáticamente apenas el invitado se registra/loguea desde
     * la pantalla de resultado — le atribuye el intento anónimo.
     * POST /api/trivia/reclamar
     */
    @PostMapping("/reclamar")
    public ResponseEntity<TriviaEstadoResponse> reclamar(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = getUser(userDetails);
        String guestToken = body.get("guestToken");
        return ResponseEntity.ok(triviaAttemptService.reclamarIntentoInvitado(user, guestToken));
    }

    /**
     * POST /api/trivia/abandonar — cierra el intento de hoy tal cual está
     * (conserva puntos ya ganados, pero ya no se puede seguir jugando hoy).
     */
    @PostMapping("/abandonar")
    public ResponseEntity<TriviaEstadoResponse> abandonar(
            @RequestParam(required = false) String guestToken,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = getUser(userDetails);
            return ResponseEntity.ok(triviaAttemptService.abandonarIntento(user));
        }
        if (guestToken == null || guestToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(triviaAttemptService.abandonarIntentoInvitado(guestToken));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private String obtenerIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // Railway está detrás de proxy — la IP real es la primera de la lista
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<TriviaRankingDto>> ranking(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = null;
        if (userDetails != null) {
            userId = getUser(userDetails).getId();
        }
        return ResponseEntity.ok(triviaAttemptService.obtenerRanking(userId));
    }
}