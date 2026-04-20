package com.example.demo.web.controllers;

import com.example.demo.application.dtos.SubscriptionDto;
import com.example.demo.application.services.SubscriptionService;
import com.example.demo.domain.subscription.UserSubscription;
import com.example.demo.domain.subscription.UserSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/subscriptions")
@CrossOrigin(origins = "http://localhost:63342")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminSubscriptionController {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionService subscriptionService;

    public AdminSubscriptionController(UserSubscriptionRepository userSubscriptionRepository,
                                       SubscriptionService subscriptionService) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.subscriptionService = subscriptionService;
    }

    /**
     * GET /api/admin/subscriptions
     * Lista todos los usuarios premium
     */
    @GetMapping
    public ResponseEntity<List<SubscriptionDto>> getAllSubscriptions() {
        List<UserSubscription> all = userSubscriptionRepository.findAll();
        List<SubscriptionDto> dtos = all.stream()
                .map(subscriptionService::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/admin/subscriptions/active
     * Lista solo suscripciones activas
     */
    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionDto>> getActiveSubscriptions() {
        List<UserSubscription> active = userSubscriptionRepository.findAllActive();
        List<SubscriptionDto> dtos = active.stream()
                .map(subscriptionService::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/admin/subscriptions/{id}
     * Detalle de una suscripción con historial de pagos
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getSubscriptionDetail(@PathVariable Long id) {
        Optional<UserSubscription> sub = userSubscriptionRepository.findById(id);
        if (sub.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Suscripción no encontrada"));
        }
        return ResponseEntity.ok(subscriptionService.toDtoWithPayments(sub.get()));
    }

    /**
     * POST /api/admin/subscriptions/{userId}/activate
     * Alta manual de suscripción premium para un usuario
     */
    @PostMapping("/{userId}/activate")
    public ResponseEntity<?> activateManually(@PathVariable Long userId,
                                              @RequestBody Map<String, Object> body) {
        try {
            UserSubscription sub = subscriptionService.activateManually(userId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.toDto(sub));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/subscriptions/{id}/cancel
     * Baja/cancelación manual de suscripción
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelManually(@PathVariable Long id) {
        Optional<UserSubscription> sub = userSubscriptionRepository.findById(id);
        if (sub.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Suscripción no encontrada"));
        }
        try {
            subscriptionService.cancelSubscription(sub.get());
            return ResponseEntity.ok(Map.of("message", "Suscripción cancelada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
