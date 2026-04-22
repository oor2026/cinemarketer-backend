package com.example.demo.web.controllers;

import com.example.demo.application.dtos.SubscriptionDto;
import com.example.demo.application.services.MercadoPagoService;
import com.example.demo.application.services.SubscriptionService;
import com.example.demo.domain.subscription.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final MercadoPagoService mercadoPagoService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public SubscriptionController(UserRepository userRepository,
                                  UserSubscriptionRepository userSubscriptionRepository,
                                  SubscriptionService subscriptionService,
                                  MercadoPagoService mercadoPagoService,
                                  SubscriptionPlanRepository subscriptionPlanRepository) {
        this.userRepository = userRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.mercadoPagoService = mercadoPagoService;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    /**
     * GET /api/subscriptions/me
     * Obtiene el estado de suscripción del usuario autenticado
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMySubscription(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);

        // Primero buscar suscripción activa
        Optional<UserSubscription> activeSub = userSubscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);

        if (activeSub.isPresent()) {
            return ResponseEntity.ok(subscriptionService.toDto(activeSub.get()));
        }

        // Si no hay activa, buscar la última cancelada (para mostrar info de vencimiento)
        Optional<UserSubscription> cancelledSub = userSubscriptionRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), SubscriptionStatus.CANCELLED);

        if (cancelledSub.isPresent()) {
            return ResponseEntity.ok(subscriptionService.toDto(cancelledSub.get()));
        }

        // No tiene suscripción — devolver datos del plan disponible
        SubscriptionDto dto = subscriptionService.getPlanInfo();
        dto.setActive(false);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/subscriptions/subscribe
     * Inicia el proceso de suscripción con Mercado Pago
     */
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);

        Optional<UserSubscription> existing = userSubscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);

        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya tenés una suscripción activa"));
        }

        try {
            Map<String, Object> mpResponse = mercadoPagoService.createSubscription(user);

            // ── NUEVO: guardar suscripción local en estado PENDING ──
            String preapprovalId = (String) mpResponse.get("preapprovalId");
            if (preapprovalId != null) {
                SubscriptionPlan plan = subscriptionService.getPlanInfo().getPlanId() != null
                        ? subscriptionPlanRepository.findFirstByActiveTrue().orElse(null)
                        : null;

                if (plan != null) {
                    UserSubscription sub = new UserSubscription();
                    sub.setUser(user);
                    sub.setPlan(plan);
                    sub.setMpPreapprovalId(preapprovalId);
                    sub.setStatus(SubscriptionStatus.PENDING);
                    userSubscriptionRepository.save(sub);
                }
            }

            return ResponseEntity.ok(mpResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar la suscripción: " + e.getMessage()));
        }
    }

    /**
     * POST /api/subscriptions/cancel
     * Cancela la suscripción del usuario autenticado
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);

        Optional<UserSubscription> sub = userSubscriptionRepository
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);

        if (sub.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No tenés una suscripción activa"));
        }

        try {
            subscriptionService.cancelSubscription(sub.get());
            return ResponseEntity.ok(Map.of("message", "Suscripción cancelada correctamente. "
                    + "Mantenés el acceso hasta el fin del período pagado."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al cancelar la suscripción: " + e.getMessage()));
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
