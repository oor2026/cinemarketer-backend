package com.example.demo.web.controllers;

import com.example.demo.application.dtos.PremiumRewardDto;
import com.example.demo.application.services.EmailService;
import com.example.demo.application.services.PremiumRewardService;
import com.example.demo.application.services.SubscriptionService;
import com.example.demo.domain.premium.PremiumDrawEntry;
import com.example.demo.domain.premium.PremiumDrawEntryRepository;
import com.example.demo.domain.premium.PremiumReward;
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
public class PremiumRewardController {

    private final UserRepository userRepository;
    private final PremiumRewardService premiumRewardService;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final PremiumDrawEntryRepository drawEntryRepository;

    public PremiumRewardController(UserRepository userRepository,
                                   PremiumRewardService premiumRewardService,
                                   SubscriptionService subscriptionService,
                                   EmailService emailService,
                                   PremiumDrawEntryRepository drawEntryRepository) {
        this.userRepository = userRepository;
        this.premiumRewardService = premiumRewardService;
        this.subscriptionService = subscriptionService;
        this.emailService = emailService;
        this.drawEntryRepository = drawEntryRepository;
    }

    @GetMapping
    public ResponseEntity<List<PremiumRewardDto>> getCatalog(
            @RequestParam(required = false) PremiumRewardType type,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        boolean isPremium = user.isActivePremium();
        List<PremiumRewardDto> catalog = premiumRewardService.getCatalog(user, isPremium, type);
        return ResponseEntity.ok(catalog);
    }

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

    /**
     * Obtener un premio premium puntual por id — usado por el carrusel del feed.
     * Reutiliza premiumRewardService.getCatalog() (sin filtro de tipo) y busca
     * el id dentro, para no duplicar el cálculo de canRedeem/alreadyEntered
     * que ya vive ahí.
     * GET /api/premium/rewards/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getRewardById(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        boolean isPremium = user.isActivePremium();
        List<PremiumRewardDto> catalog = premiumRewardService.getCatalog(user, isPremium, null);

        return catalog.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Premio no encontrado")));
    }

    @GetMapping("/draws/me")
    public ResponseEntity<List<Map<String, Object>>> getMyDrawEntries(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        List<PremiumDrawEntry> entries = drawEntryRepository.findByUserIdOrderByEnteredAtDesc(user.getId());

        List<Map<String, Object>> result = entries.stream().map(e -> {
            PremiumReward reward = e.getReward();
            boolean gano = reward.getWinner() != null && reward.getWinner().getId().equals(user.getId());
            return Map.<String, Object>of(
                    "id", e.getId(),
                    "rewardId", reward.getId(),
                    "rewardName", reward.getName(),
                    "rewardImageUrl", reward.getImageUrl() != null ? reward.getImageUrl() : "",
                    "enteredAt", e.getEnteredAt().toString(),
                    "drawExecuted", reward.isDrawExecuted(),
                    "won", gano,
                    "drawDate", reward.getDrawDate() != null ? reward.getDrawDate().toString() : ""
            );
        }).toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Versión pública (sin auth) de un premio especial puntual — para el
     * link compartido. No expone canRedeem/alreadyEntered ni nada personal.
     * GET /api/premium/rewards/public/{id}
     */
    /**
     * Versión pública (sin auth) de un premio especial puntual — para el
     * link compartido. No usa getCatalog/toDto porque esos internamente
     * llaman a user.isActivePremium() sin chequeo de null (línea 351 de
     * PremiumRewardService) — con un visitante sin sesión eso tira NPE.
     * Acá resolvemos directo contra el repository, sin ningún dato
     * personalizado (canRedeem, alreadyEntered, etc. no aplican para un
     * visitante sin cuenta).
     * GET /api/premium/rewards/public/{id}
     */
    @GetMapping("/public/{id}")
    public ResponseEntity<?> getRewardPublic(@PathVariable Long id) {
        return premiumRewardService.getRewardEntityById(id)
                .<ResponseEntity<?>>map(r -> {
                    com.example.demo.application.dtos.RewardPublicDto dto = new com.example.demo.application.dtos.RewardPublicDto();
                    dto.setId(r.getId());
                    dto.setName(r.getName());
                    dto.setDescription(r.getDescription());
                    dto.setImageUrl(r.getImageUrl());
                    dto.setPointsRequired(r.getPointsRequired());
                    dto.setTipo("ESPECIAL");
                    return ResponseEntity.ok((Object) dto);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Premio no encontrado")));
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
