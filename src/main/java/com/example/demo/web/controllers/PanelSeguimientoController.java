package com.example.demo.web.controllers;

import com.example.demo.application.services.PanelSeguimientoService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Panel de seguimiento (solo Creator activo). Sin sesión responde 401 (lo hace SecurityConfig con
 * anyRequest().authenticated()); con sesión pero sin Creator activo responde 403.
 * "Creator activo" = User.isActiveCreator(): bandera de Creator + vencimiento a futuro. Si el usuario
 * canceló pero sigue dentro del período ya pago, conserva el acceso, igual que Premium.
 */
@RestController
@RequestMapping("/api/creator/panel")
public class PanelSeguimientoController {

    private final PanelSeguimientoService panelService;
    private final UserRepository userRepository;

    public PanelSeguimientoController(PanelSeguimientoService panelService, UserRepository userRepository) {
        this.panelService = panelService;
        this.userRepository = userRepository;
    }

    private User creatorActivo(UserDetails ud) {
        if (ud == null) return null;
        return userRepository.findByEmail(ud.getUsername())
                .filter(User::isActiveCreator)
                .orElse(null);
    }

    private ResponseEntity<Object> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "El Panel de seguimiento es solo para Creators con suscripción activa."));
    }

    // GET /api/creator/panel/access  → 200 si puede ver el panel, 403 si no. El front lo usa para mostrar el botón.
    @GetMapping("/access")
    public ResponseEntity<Object> acceso(@AuthenticationPrincipal UserDetails ud) {
        User user = creatorActivo(ud);
        if (user == null) return prohibido();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("creator", true);
        body.put("creatorUntil", user.getCreatorUntil());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/summary")
    public ResponseEntity<Object> resumen(@AuthenticationPrincipal UserDetails ud,
                                          @RequestParam(required = false) Integer days) {
        User user = creatorActivo(ud);
        if (user == null) return prohibido();
        return ResponseEntity.ok(panelService.resumen(user.getId(), PanelSeguimientoService.normalizarDias(days)));
    }

    @GetMapping("/timeline")
    public ResponseEntity<Object> evolucion(@AuthenticationPrincipal UserDetails ud,
                                            @RequestParam(required = false) Integer days) {
        User user = creatorActivo(ud);
        if (user == null) return prohibido();
        return ResponseEntity.ok(panelService.timeline(user.getId(), PanelSeguimientoService.normalizarDias(days)));
    }

    // sort = recent (por defecto) | visualizaciones | bancos | puntos | comentarios
    @GetMapping("/publications")
    public ResponseEntity<Object> publicaciones(@AuthenticationPrincipal UserDetails ud,
                                                @RequestParam(required = false) Integer days,
                                                @RequestParam(required = false) String sort) {
        User user = creatorActivo(ud);
        if (user == null) return prohibido();
        return ResponseEntity.ok(panelService.publicaciones(user.getId(),
                PanelSeguimientoService.normalizarDias(days), sort));
    }

    @GetMapping("/publications/{id}")
    public ResponseEntity<Object> detalle(@AuthenticationPrincipal UserDetails ud,
                                          @PathVariable Long id,
                                          @RequestParam(required = false) Integer days) {
        User user = creatorActivo(ud);
        if (user == null) return prohibido();
        // Si no es suya (o no está activa) responde igual 404: no se revela si existe.
        if (!panelService.esDelAutor(user.getId(), id)) return ResponseEntity.notFound().build();
        Map<String, Object> d = panelService.detalle(user.getId(), id, PanelSeguimientoService.normalizarDias(days));
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @GetMapping("/status")
    public ResponseEntity<Object> estado(@AuthenticationPrincipal UserDetails ud,
                                         @RequestParam(required = false) Integer days) {
        User user = creatorActivo(ud);
        if (user == null) return prohibido();
        return ResponseEntity.ok(panelService.estado(user.getId(), PanelSeguimientoService.normalizarDias(days)));
    }

    @GetMapping("/insights")
    public ResponseEntity<Object> insights(@AuthenticationPrincipal UserDetails ud,
                                           @RequestParam(required = false) Integer days) {
        User user = creatorActivo(ud);
        if (user == null) return prohibido();
        return ResponseEntity.ok(panelService.insights(user.getId(), PanelSeguimientoService.normalizarDias(days)));
    }
}
