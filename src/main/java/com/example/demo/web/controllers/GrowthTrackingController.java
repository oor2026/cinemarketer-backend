package com.example.demo.web.controllers;

import com.example.demo.application.services.GrowthTrackingService;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints de registro de las métricas de crecimiento (visitas al perfil y visitas por link compartido).
 * Siempre responden 204: el front los dispara "y se olvida".
 */
@RestController
@RequestMapping("/api")
public class GrowthTrackingController {

    private final GrowthTrackingService growthTrackingService;
    private final UserRepository userRepository;

    public GrowthTrackingController(GrowthTrackingService growthTrackingService, UserRepository userRepository) {
        this.growthTrackingService = growthTrackingService;
        this.userRepository = userRepository;
    }

    // POST /api/profile-views/{userId}   (con sesión)
    @PostMapping("/profile-views/{userId}")
    public ResponseEntity<Void> visitaPerfil(@AuthenticationPrincipal UserDetails ud, @PathVariable Long userId) {
        if (ud != null) {
            userRepository.findByEmail(ud.getUsername())
                    .ifPresent(u -> growthTrackingService.registrarVisitaPerfil(u.getId(), userId));
        }
        return ResponseEntity.noContent().build();
    }

    // POST /api/publications/public/{id}/share-visit   body: { "anonKey": "<uuid>", "ref": "u28" }   (SIN sesión)
    // Queda abierto por el permitAll de "/api/publications/public/**" en SecurityConfig.
    @PostMapping("/publications/public/{id}/share-visit")
    public ResponseEntity<Void> visitaPorLink(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object clave = body.get("anonKey");
        Object ref = body.get("ref");
        growthTrackingService.registrarVisitaDesdeLink(
                id, clave == null ? null : String.valueOf(clave), ref == null ? null : String.valueOf(ref));
        return ResponseEntity.noContent().build();
    }
}
