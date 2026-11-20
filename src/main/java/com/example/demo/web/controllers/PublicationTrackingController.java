package com.example.demo.web.controllers;

import com.example.demo.application.services.PublicationTrackingService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Endpoints de registro de métricas (visualizaciones y clics en compartir).
 * Siempre responden 204: el front los dispara "y se olvida", y no se le filtra
 * si la publicación existe, es visible o si el conteo se descartó.
 */
@RestController
@RequestMapping("/api/publications")
public class PublicationTrackingController {

    private final PublicationTrackingService trackingService;
    private final UserRepository userRepository;

    public PublicationTrackingController(PublicationTrackingService trackingService,
                                         UserRepository userRepository) {
        this.trackingService = trackingService;
        this.userRepository = userRepository;
    }

    private User getUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    // POST /api/publications/views   body: { "ids": [12, 15, 18] }   (con sesión)
    @PostMapping("/views")
    public ResponseEntity<Void> vistasFeed(@AuthenticationPrincipal UserDetails ud,
                                           @RequestBody Map<String, Object> body) {
        User user = getUser(ud);
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> lista) {
            for (Object o : lista) {
                try {
                    ids.add(Long.valueOf(String.valueOf(o)));
                } catch (NumberFormatException ignorado) {
                    // un id inválido no invalida el resto del lote
                }
            }
        }
        trackingService.registrarVistasFeed(user, ids);
        return ResponseEntity.noContent().build();
    }

    // POST /api/publications/{id}/share-click   (con sesión)
    @PostMapping("/{id}/share-click")
    public ResponseEntity<Void> clicCompartir(@AuthenticationPrincipal UserDetails ud,
                                              @PathVariable Long id) {
        trackingService.registrarClicCompartir(getUser(ud), id);
        return ResponseEntity.noContent().build();
    }

    // POST /api/publications/public/{id}/view   body: { "anonKey": "<uuid>" }   (SIN sesión)
    // Ya queda abierto por el permitAll de "/api/publications/public/**" en SecurityConfig.
    @PostMapping("/public/{id}/view")
    public ResponseEntity<Void> vistaPublica(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        Object clave = body.get("anonKey");
        trackingService.registrarVistaPublica(id, clave == null ? null : String.valueOf(clave));
        return ResponseEntity.noContent().build();
    }
}
