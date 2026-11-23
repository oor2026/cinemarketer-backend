package com.example.demo.web.controllers;

import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.application.services.JuegoPublicoService;
import com.example.demo.application.services.JuegoPublicoService.ResponderRequest;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Centro de actividad (público). No hace falta tocar SecurityConfig:
 *   GET  /api/public/juegos/{slug}             landing: entra por "/api/public/**" (sin login).
 *                                               Si llega con token, suma "yo" (mi posición, mi partida de hoy, mi premio).
 *   POST /api/juegos/{slug}/iniciar            empieza o retoma la partida de hoy   ┐
 *   POST /api/juegos/{slug}/responder          { "preguntaId": 12, "opcion": 3 }    ├ con login ("anyRequest().authenticated()")
 *   POST /api/juegos/{slug}/reclamar           el ganador confirma su premio        ┘
 */
@RestController
public class JuegosPublicoController {

    private final JuegoPublicoService service;
    private final UserRepository userRepository;

    public JuegosPublicoController(JuegoPublicoService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/public/juegos/{slug}")
    public ResponseEntity<?> landing(@PathVariable String slug, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.landing(slug, usuarioOpcional(userDetails)));
    }

    @PostMapping("/api/juegos/{slug}/iniciar")
    public ResponseEntity<?> iniciar(@PathVariable String slug, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.iniciar(slug, usuario(userDetails)));
    }

    @PostMapping("/api/juegos/{slug}/responder")
    public ResponseEntity<?> responder(@PathVariable String slug, @RequestBody ResponderRequest req,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.responder(slug, usuario(userDetails), req));
    }

    @PostMapping("/api/juegos/{slug}/reclamar")
    public ResponseEntity<?> reclamar(@PathVariable String slug, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(service.reclamar(slug, usuario(userDetails)));
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
