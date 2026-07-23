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

import java.util.List;
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

    @GetMapping("/me")
    public ResponseEntity<?> getMySubscription(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);

        List<UserSubscription> activeSubs = userSubscriptionRepository
                .findAllByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE);

        if (!activeSubs.isEmpty()) {
            UserSubscription elegida = activeSubs.stream()
                    .filter(s -> "Premium".equalsIgnoreCase(s.getPlan().getName()))
                    .findFirst()
                    .orElse(activeSubs.get(0));
            return ResponseEntity.ok(subscriptionService.toDto(elegida));
        }

        Optional<UserSubscription> cancelledSub = userSubscriptionRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), SubscriptionStatus.CANCELLED);

        if (cancelledSub.isPresent()) {
            return ResponseEntity.ok(subscriptionService.toDto(cancelledSub.get()));
        }

        SubscriptionDto dto = subscriptionService.getPlanInfo();
        dto.setActive(false);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestParam(defaultValue = "Premium") String plan) {
        User user = getAuthenticatedUser(userDetails);

        Optional<UserSubscription> existing = userSubscriptionRepository
                .findByUserIdAndStatusAndPlanName(user.getId(), SubscriptionStatus.ACTIVE, plan);

        if (existing.isPresent()) {
            UserSubscription sub = existing.get();
            // Si la suscripción venció, marcarla como EXPIRED y permitir nueva suscripción
            if (!sub.isActive()) {
                sub.setStatus(SubscriptionStatus.EXPIRED);
                userSubscriptionRepository.save(sub);
                if ("Creator".equalsIgnoreCase(plan)) {
                    user.setCreator(false);
                    user.setCreatorUntil(null);
                } else {
                    user.setPremium(false);
                    user.setPremiumUntil(null);
                }
                userRepository.save(user);
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Ya tenés una suscripción " + plan + " activa"));
            }
        }

        try {
            Map<String, Object> mpResponse = mercadoPagoService.createSubscription(user, plan);
            return ResponseEntity.ok(mpResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar la suscripción: " + e.getMessage()));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam(defaultValue = "Premium") String plan) {
        User user = getAuthenticatedUser(userDetails);

        Optional<UserSubscription> sub = userSubscriptionRepository
                .findByUserIdAndStatusAndPlanName(user.getId(), SubscriptionStatus.ACTIVE, plan);

        if (sub.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No tenés una suscripción " + plan + " activa"));
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

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
