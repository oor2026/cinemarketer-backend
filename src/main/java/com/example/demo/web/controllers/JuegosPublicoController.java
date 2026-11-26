package com.example.demo.web.controllers;

import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.application.services.JuegoPublicoService;
import com.example.demo.application.services.JuegoParticipanteService.AnotarseRequest;
import com.example.demo.application.services.JuegoPublicoService.ResponderRequest;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Centro de actividad (público). No hace falta tocar SecurityConfig.
 *
 * Campañas con cuenta de Cinemarketer (como siempre):
 *   GET  /api/public/juegos/{slug}             landing: entra por "/api/public/**" (sin login).
 *                                               Si llega con token, suma "yo" (mi posición, mi partida de hoy, mi premio).
 *   POST /api/juegos/{slug}/iniciar            empieza o retoma la partida de hoy   ┐
 *   POST /api/juegos/{slug}/responder          { "preguntaId": 12, "opcion": 3 }    ├ con login ("anyRequest().authenticated()")
 *   POST /api/juegos/{slug}/reclamar           el ganador confirma su premio        ┘
 *
 * Campañas SIN_REGISTRO (solo nombre y email). Todo bajo "/api/public/**" y
 * el jugador se identifica con el parámetro "t" (su token):
 *   POST /api/public/juegos/{slug}/anotarse    { "nombre", "email", "aceptaBases": true, "aceptaNovedades": false }
 *                                               → { "token", "nombre", "yaEstabaAnotado" }
 *   POST /api/public/juegos/{slug}/iniciar?t=...
 *   POST /api/public/juegos/{slug}/responder?t=...
 *   POST /api/public/juegos/{slug}/reclamar?t=...
 *   (la landing GET también lee "t" para armar "yo").
 */
@RestController
public class JuegosPublicoController {

    // El token del participante sin registro viaja como parámetro (?t=...), no como
    // header: así no depende de qué headers permita la configuración de CORS.
    static final String PARAM_TOKEN = "t";

    private final JuegoPublicoService service;
    private final UserRepository userRepository;

    public JuegosPublicoController(JuegoPublicoService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/public/juegos/{slug}")
    public ResponseEntity<?> landing(@PathVariable String slug, @AuthenticationPrincipal UserDetails userDetails,
                                     @RequestParam(value = PARAM_TOKEN, required = false) String token) {
        return ResponseEntity.ok(service.landing(slug, usuarioOpcional(userDetails), token));
    }

    // ---------- Con cuenta de Cinemarketer ----------
    @PostMapping("/api/juegos/{slug}/iniciar")
    public ResponseEntity<?> iniciar(@PathVariable String slug, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.iniciar(slug, usuario(userDetails), null));
    }

    @PostMapping("/api/juegos/{slug}/responder")
    public ResponseEntity<?> responder(@PathVariable String slug, @RequestBody ResponderRequest req,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.responder(slug, usuario(userDetails), null, req));
    }

    @PostMapping("/api/juegos/{slug}/reclamar")
    public ResponseEntity<?> reclamar(@PathVariable String slug, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.reclamar(slug, usuario(userDetails), null));
    }

    // ---------- Sin registro ----------
    @PostMapping("/api/public/juegos/{slug}/anotarse")
    public ResponseEntity<?> anotarse(@PathVariable String slug, @RequestBody AnotarseRequest req) {
        return ResponseEntity.ok(service.anotarse(slug, req));
    }

    @PostMapping("/api/public/juegos/{slug}/iniciar")
    public ResponseEntity<?> iniciarSinRegistro(@PathVariable String slug,
                                                @RequestParam(value = PARAM_TOKEN, required = false) String token) {
        return ResponseEntity.ok(service.iniciar(slug, null, token));
    }

    @PostMapping("/api/public/juegos/{slug}/responder")
    public ResponseEntity<?> responderSinRegistro(@PathVariable String slug, @RequestBody ResponderRequest req,
                                                  @RequestParam(value = PARAM_TOKEN, required = false) String token) {
        return ResponseEntity.ok(service.responder(slug, null, token, req));
    }

    @PostMapping("/api/public/juegos/{slug}/reclamar")
    public ResponseEntity<?> reclamarSinRegistro(@PathVariable String slug,
                                                 @RequestParam(value = PARAM_TOKEN, required = false) String token) {
        return ResponseEntity.ok(service.reclamar(slug, null, token));
    }

    private User usuario(UserDetails userDetails) {
        User u = usuarioOpcional(userDetails);
        if (u == null) throw new JuegoException(401, "Ingresá a tu cuenta para jugar");
        return u;
    }

    private User usuarioOpcional(UserDetails userDetails) {
        if (userDetails == null) return null;
        return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
    }

    @ExceptionHandler(JuegoException.class)
    public ResponseEntity<?> error(JuegoException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
