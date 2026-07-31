package com.example.demo.web.controllers;

import com.example.demo.application.dtos.TriviaEstadoResponse;
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
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = getUser(userDetails);
            return ResponseEntity.ok(triviaAttemptService.obtenerOCrearIntentoDeHoy(user));
        }
        if (guestToken == null || guestToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(triviaAttemptService.obtenerOCrearIntentoInvitado(guestToken));
    }

    /**
     * POST /api/trivia/responder
     */
    @PostMapping("/responder")
    public ResponseEntity<TriviaRespuestaResponse> responder(
            @RequestBody TriviaRespuestaRequest request,
            @RequestParam(required = false) String guestToken,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = getUser(userDetails);
            return ResponseEntity.ok(triviaAttemptService.responder(user, request.getOpcionElegida()));
        }
        if (guestToken == null || guestToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(triviaAttemptService.responderInvitado(guestToken, request.getOpcionElegida()));
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

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}