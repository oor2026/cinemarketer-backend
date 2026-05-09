package com.example.demo.web.controllers;

import com.example.demo.application.services.EmailService;
import com.example.demo.domain.premium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/premium/redemptions")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminPremiumRedemptionController {

    private static final Logger log = LoggerFactory.getLogger(AdminPremiumRedemptionController.class);

    private final PremiumRedemptionRepository premiumRedemptionRepository;
    private final EmailService emailService;

    public AdminPremiumRedemptionController(PremiumRedemptionRepository premiumRedemptionRepository,
                                            EmailService emailService) {
        this.premiumRedemptionRepository = premiumRedemptionRepository;
        this.emailService = emailService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<PremiumRedemption> redemptions = premiumRedemptionRepository.findByDeletedFalse();
        return ResponseEntity.ok(redemptions.stream().map(this::toMap).collect(Collectors.toList()));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Map<String, Object>>> getByStatus(@PathVariable PremiumRedemptionStatus status) {
        List<PremiumRedemption> redemptions = premiumRedemptionRepository.findByStatusAndDeletedFalse(status);
        return ResponseEntity.ok(redemptions.stream().map(this::toMap).collect(Collectors.toList()));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        PremiumRedemption redemption = premiumRedemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje premium no encontrado"));

        String newStatus = body.get("status");
        if (newStatus != null) {
            PremiumRedemptionStatus status = PremiumRedemptionStatus.valueOf(newStatus);
            redemption.setStatus(status);
            premiumRedemptionRepository.save(redemption);

            if (status == PremiumRedemptionStatus.COMPLETED && redemption.getUser() != null && redemption.getReward() != null) {
                try {
                    emailService.sendPremiumRedemptionCompletedEmail(
                            redemption.getUser().getEmail(),
                            redemption.getUser().getName(),
                            redemption.getReward().getName(),
                            redemption.getRedemptionCode()
                    );
                } catch (Exception e) {
                    log.warn("No se pudo enviar mail de retiro premium: {}", e.getMessage());
                }
            }
        }

        return ResponseEntity.ok(toMap(redemption));
    }

    @DeleteMapping("/{id}/delete")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id) {
        PremiumRedemption redemption = premiumRedemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje premium no encontrado"));
        redemption.setDeleted(true);
        premiumRedemptionRepository.save(redemption);
        return ResponseEntity.ok(Map.of("message", "Canje premium eliminado correctamente"));
    }

    private Map<String, Object> toMap(PremiumRedemption r) {
        return Map.of(
                "id", r.getId(),
                "rewardName", r.getReward().getName(),
                "rewardImageUrl", r.getReward().getImageUrl() != null ? r.getReward().getImageUrl() : "",
                "userName", r.getUser().getName(),
                "userEmail", r.getUser().getEmail(),
                "pointsSpent", r.getPointsSpent(),
                "redemptionCode", r.getRedemptionCode() != null ? r.getRedemptionCode() : "",
                "status", r.getStatus().toString(),
                "redeemedAt", r.getRedeemedAt().toString()
        );
    }
}