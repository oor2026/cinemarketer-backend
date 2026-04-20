package com.example.demo.web.controllers;

import com.example.demo.application.dtos.PremiumRewardDto;
import com.example.demo.application.services.PremiumRewardService;
import com.example.demo.application.services.SubscriptionService;
import com.example.demo.domain.premium.PremiumRewardType;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/premium/rewards")
@CrossOrigin(origins = "http://localhost:63342")
public class PremiumRewardController {

    private final UserRepository userRepository;
    private final PremiumRewardService premiumRewardService;
    private final SubscriptionService subscriptionService;

    public PremiumRewardController(UserRepository userRepository,
                                   PremiumRewardService premiumRewardService,
                                   SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.premiumRewardService = premiumRewardService;
        this.subscriptionService = subscriptionService;
    }

    /**
     * GET /api/premium/rewards
     * Catálogo de premios especiales — visible para todos
     * El DTO indica si el usuario puede operar o no
     */
    @GetMapping
    public ResponseEntity<List<PremiumRewardDto>> getCatalog(
            @RequestParam(required = false) PremiumRewardType type,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getAuthenticatedUser(userDetails);
        boolean isPremium = user.isActivePremium();
        List<PremiumRewardDto> catalog = premiumRewardService.getCatalog(user, isPremium, type);
        return ResponseEntity.ok(catalog);
    }

    /**
     * POST /api/premium/rewards/{id}/redeem
     * Canjear un premio CANJEABLE con puntos
     */
    @PostMapping("/{id}/redeem")
    public ResponseEntity<?> redeem(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);

        if (!user.isActivePremium()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Necesitás una suscripción Premium activa para canjear este premio"));
        }

        try {
            Map<String, Object> result = premiumRewardService.redeemReward(user, id);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/premium/rewards/{id}/enter
     * Anotarse en un SORTEO gratuito
     */
    @PostMapping("/{id}/enter")
    public ResponseEntity<?> enterDraw(@PathVariable Long id,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);

        if (!user.isActivePremium()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Necesitás una suscripción Premium activa para participar en sorteos"));
        }

        try {
            premiumRewardService.enterDraw(user, id);
            return ResponseEntity.ok(Map.of("message", "¡Te anotaste al sorteo correctamente!"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
