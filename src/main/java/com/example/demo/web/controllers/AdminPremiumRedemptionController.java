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
    private final PremiumRedemptionDeliveryPointRepository deliveryPointRepository;
    private final EmailService emailService;

    public AdminPremiumRedemptionController(PremiumRedemptionRepository premiumRedemptionRepository,
                                            PremiumRedemptionDeliveryPointRepository deliveryPointRepository,
                                            EmailService emailService) {
        this.premiumRedemptionRepository = premiumRedemptionRepository;
        this.deliveryPointRepository = deliveryPointRepository;
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        PremiumRedemption redemption = premiumRedemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje premium no encontrado"));
        return ResponseEntity.ok(toMap(redemption, true));
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

    // ==============================================
    // PUNTOS DE ENTREGA — mismo criterio que el lado Free.
    // ==============================================

    @GetMapping("/{id}/delivery-points")
    public ResponseEntity<List<Map<String, Object>>> getDeliveryPoints(@PathVariable Long id) {
        List<Map<String, Object>> points = deliveryPointRepository.findByRedemptionIdOrderByDisplayOrderAsc(id)
                .stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "locationReference", p.getLocationReference(),
                        "scheduleInfo", p.getScheduleInfo(),
                        "displayOrder", p.getDisplayOrder()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(points);
    }

    @PostMapping("/{id}/delivery-points")
    @Transactional
    public ResponseEntity<?> addDeliveryPoint(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PremiumRedemption redemption = premiumRedemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje premium no encontrado"));

        PremiumRedemptionDeliveryPoint point = new PremiumRedemptionDeliveryPoint();
        point.setRedemption(redemption);
        point.setLocationReference((String) body.get("locationReference"));
        point.setScheduleInfo((String) body.get("scheduleInfo"));
        point.setDisplayOrder(body.get("displayOrder") != null ? (Integer) body.get("displayOrder") : 0);
        deliveryPointRepository.save(point);

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(Map.of(
                "id", point.getId(),
                "locationReference", point.getLocationReference(),
                "scheduleInfo", point.getScheduleInfo()
        ));
    }

    @DeleteMapping("/delivery-points/{pointId}")
    @Transactional
    public ResponseEntity<?> deleteDeliveryPoint(@PathVariable Long pointId) {
        deliveryPointRepository.deleteById(pointId);
        return ResponseEntity.ok(Map.of("message", "Punto de entrega eliminado"));
    }

    private Map<String, Object> toMap(PremiumRedemption r) {
        return toMap(r, false);
    }

    // includeDeliveryPoints solo para el detalle puntual — mismo
    // criterio que AdminRedemptionController#toDto. Uso HashMap en vez
    // de Map.of() porque este último no admite valores null
    // (chosenDeliveryPointId lo es hasta que el usuario elige) ni más
    // de 10 entradas, y ya nos quedamos cortos con los campos nuevos.
    private Map<String, Object> toMap(PremiumRedemption r, boolean includeDeliveryPoints) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", r.getId());
        map.put("rewardId", r.getReward().getId());
        map.put("rewardName", r.getReward().getName());
        map.put("rewardImageUrl", r.getReward().getImageUrl() != null ? r.getReward().getImageUrl() : "");
        map.put("rewardPartner", r.getReward().getPartner() != null ? r.getReward().getPartner() : "");
        map.put("rewardWebsite", r.getReward().getWebsite() != null ? r.getReward().getWebsite() : "");
        map.put("rewardTermsConditions", r.getReward().getTermsConditions() != null ? r.getReward().getTermsConditions() : "");
        map.put("rewardPointsRequired", r.getReward().getPointsRequired());
        map.put("rewardDeliveryMethod", r.getReward().getDeliveryMethod() != null ? r.getReward().getDeliveryMethod() : "");
        map.put("userName", r.getUser().getName());
        map.put("userEmail", r.getUser().getEmail());
        map.put("pointsSpent", r.getPointsSpent());
        map.put("redemptionCode", r.getRedemptionCode() != null ? r.getRedemptionCode() : "");
        map.put("status", r.getStatus().toString());
        map.put("redeemedAt", r.getRedeemedAt().toString());
        map.put("deliveryAddress", r.getDeliveryAddress() != null ? r.getDeliveryAddress() : "");
        map.put("chosenDeliveryPointId", r.getChosenDeliveryPoint() != null ? r.getChosenDeliveryPoint().getId() : null);

        if (includeDeliveryPoints) {
            map.put("deliveryPoints", deliveryPointRepository.findByRedemptionIdOrderByDisplayOrderAsc(r.getId())
                    .stream()
                    .map(p -> Map.of("id", p.getId(), "locationReference", p.getLocationReference(), "scheduleInfo", p.getScheduleInfo()))
                    .collect(Collectors.toList()));
        }

        return map;
    }
}