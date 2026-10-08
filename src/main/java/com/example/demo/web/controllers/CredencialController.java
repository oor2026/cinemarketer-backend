package com.example.demo.web.controllers;

import com.example.demo.application.services.CredencialService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Credencial Digital Cinéfila.
 *
 * GET /api/credencial                          — con login: genera la credencial del usuario.
 *     200 → CredencialDto
 *     409 → { "faltantes": [ { "codigo": "DNI", "mensaje": "..." } ] }
 *     403 → { "message": "..." }   (cuenta demo, suspendida o sin verificar)
 *     503 → { "message": "..." }   (falta credencial.secret en el servidor)
 *
 * GET /api/public/credencial/verificar?t=...   — sin login: lo que ve el comercio
 *     al escanear el QR. Siempre 200, el resultado va en "estado". Queda
 *     público por la regla "/api/public/**" que ya existe en SecurityConfig.
 */
@RestController
public class CredencialController {

    private final CredencialService credencialService;
    private final UserRepository userRepository;

    public CredencialController(CredencialService credencialService, UserRepository userRepository) {
        this.credencialService = credencialService;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/credencial")
    public ResponseEntity<?> generar() {
        User user = getAuthenticatedUser();
        try {
            CredencialService.Resultado resultado = credencialService.generar(user);
            if (!resultado.generada()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .header("Cache-Control", "no-store")
                        .body(Map.of("faltantes", resultado.faltantes()));
            }
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(resultado.credencial());
        } catch (CredencialService.CuentaNoHabilitadaException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (CredencialService.CredencialNoConfiguradaException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/api/public/credencial/verificar")
    public ResponseEntity<?> verificar(@RequestParam(name = "t", required = false) String token) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(credencialService.verificar(token));
    }

    private User getAuthenticatedUser() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
