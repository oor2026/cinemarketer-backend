package com.example.demo.web.controllers;

import com.example.demo.domain.push.PushSubscription;
import com.example.demo.domain.push.PushSubscriptionRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushSubscriptionController {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;

    @Value("${vapid.public-key}")
    private String vapidPublicKey;

    public PushSubscriptionController(PushSubscriptionRepository pushSubscriptionRepository,
                                      UserRepository userRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.userRepository = userRepository;
    }

    // GET /api/push/vapid-public-key — devuelve la clave pública VAPID al frontend
    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }

    // POST /api/push/subscribe — guardar suscripción
    @PostMapping("/subscribe")
    @Transactional
    public ResponseEntity<?> subscribe(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String endpoint = (String) body.get("endpoint");
        Map<String, String> keys = (Map<String, String>) body.get("keys");
        String p256dh = keys.get("p256dh");
        String auth = keys.get("auth");

        // Upsert: si ya existe para este usuario+endpoint, no duplicar
        pushSubscriptionRepository.findByUserIdAndEndpoint(user.getId(), endpoint)
                .ifPresentOrElse(
                        existing -> {
                            existing.setP256dh(p256dh);
                            existing.setAuth(auth);
                            pushSubscriptionRepository.save(existing);
                        },
                        () -> {
                            PushSubscription sub = new PushSubscription();
                            sub.setUser(user);
                            sub.setEndpoint(endpoint);
                            sub.setP256dh(p256dh);
                            sub.setAuth(auth);
                            pushSubscriptionRepository.save(sub);
                        }
                );

        return ResponseEntity.ok(Map.of("message", "Suscripción registrada"));
    }

    // DELETE /api/push/unsubscribe — eliminar suscripción
    @DeleteMapping("/unsubscribe")
    @Transactional
    public ResponseEntity<?> unsubscribe(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        pushSubscriptionRepository.deleteByUserIdAndEndpoint(user.getId(), body.get("endpoint"));
        return ResponseEntity.ok(Map.of("message", "Suscripción eliminada"));
    }
}