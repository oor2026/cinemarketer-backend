package com.example.demo.web.controllers;

import com.example.demo.application.services.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionConfirmController {

    private final SubscriptionService subscriptionService;

    public SubscriptionConfirmController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * GET /api/subscriptions/confirm?token=ABC123
     * El usuario hace click en el link del email → si está logueado activa su suscripción
     */
    @GetMapping("/confirm")
    public ResponseEntity<?> confirmarSuscripcion(
            @RequestParam String token,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            // No está logueado — redirigir al login con el token como parámetro
            return ResponseEntity.status(302)
                    .header("Location", "https://cinemarketer.com.ar/login?redirect=/confirmar-suscripcion&token=" + token)
                    .build();
        }

        try {
            subscriptionService.confirmarSuscripcionPendiente(token, userDetails.getUsername());
            return ResponseEntity.ok(Map.of(
                    "message", "¡Suscripción activada correctamente! Ya sos parte de Cinemarketer Premium.",
                    "redirect", "https://cinemarketer.com.ar/dashboard?module=mi-cuenta"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}