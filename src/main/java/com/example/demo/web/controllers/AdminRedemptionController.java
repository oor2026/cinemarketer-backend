package com.example.demo.web.controllers;

import com.example.demo.application.dtos.RedemptionAdminDto;
import com.example.demo.application.dtos.UserBasicDto;
import com.example.demo.application.dtos.RewardBasicDto;
import com.example.demo.application.services.EmailService;
import com.example.demo.domain.redemption.Redemption;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.redemption.RedemptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/redemptions")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminRedemptionController {

    private static final Logger log = LoggerFactory.getLogger(AdminRedemptionController.class);

    private final RedemptionRepository redemptionRepository;
    private final com.example.demo.domain.redemption.RedemptionDeliveryPointRepository deliveryPointRepository;
    private final EmailService emailService;

    public AdminRedemptionController(RedemptionRepository redemptionRepository,
                                     com.example.demo.domain.redemption.RedemptionDeliveryPointRepository deliveryPointRepository,
                                     EmailService emailService) {
        this.redemptionRepository = redemptionRepository;
        this.deliveryPointRepository = deliveryPointRepository;
        this.emailService = emailService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRedemptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<Redemption> pageResult = redemptionRepository.findByDeletedFalse(
                    PageRequest.of(page, size, Sort.by("redemptionDate").descending()));

            List<RedemptionAdminDto> redemptions = pageResult.getContent().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("redemptions", redemptions);
            response.put("currentPage", pageResult.getNumber());
            response.put("totalItems", pageResult.getTotalElements());
            response.put("totalPages", pageResult.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<RedemptionAdminDto>> getRedemptionsByStatus(
            @PathVariable RedemptionStatus status) {
        List<Redemption> redemptions = redemptionRepository.findByStatusAndDeletedFalse(status);
        List<RedemptionAdminDto> dtos = redemptions.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RedemptionAdminDto> getRedemptionById(@PathVariable Long id) {
        Redemption redemption = redemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje no encontrado"));
        return ResponseEntity.ok(toDto(redemption, true));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<RedemptionAdminDto> updateRedemptionStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Redemption redemption = redemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje no encontrado"));

        String newStatus = body.get("status");
        if (newStatus != null) {
            RedemptionStatus status = RedemptionStatus.valueOf(newStatus);

            if (status == RedemptionStatus.COMPLETED) {
                redemption.markAsUsed();
            } else {
                redemption.setStatus(status);
            }

            redemptionRepository.save(redemption);

            // Disparar mail si el canje fue completado
            if (status == RedemptionStatus.COMPLETED && redemption.getUser() != null && redemption.getReward() != null) {
                try {
                    emailService.sendRedemptionCompletedEmail(
                            redemption.getUser().getEmail(),
                            redemption.getUser().getName(),
                            redemption.getReward().getName(),
                            redemption.getRedemptionCode()
                    );
                } catch (Exception e) {
                    log.warn("No se pudo enviar mail de canje completado: {}", e.getMessage());
                }
            }
        }

        return ResponseEntity.ok(toDto(redemption));
    }

    // ==============================================
    // PUNTOS DE ENTREGA — se cargan por canje puntual,
    // no se reutilizan entre canjes (ver diseño acordado).
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
        Redemption redemption = redemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje no encontrado"));

        com.example.demo.domain.redemption.RedemptionDeliveryPoint point = new com.example.demo.domain.redemption.RedemptionDeliveryPoint();
        point.setRedemption(redemption);
        point.setLocationReference((String) body.get("locationReference"));
        point.setScheduleInfo((String) body.get("scheduleInfo"));
        point.setDisplayOrder(body.get("displayOrder") != null ? (Integer) body.get("displayOrder") : 0);
        deliveryPointRepository.save(point);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
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

    @DeleteMapping("/{id}/delete")
    @Transactional
    public ResponseEntity<?> deleteRedemption(@PathVariable Long id) {
        Redemption redemption = redemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje no encontrado"));
        redemption.setDeleted(true);
        redemptionRepository.save(redemption);
        return ResponseEntity.ok(Map.of("message", "Canje eliminado correctamente"));
    }

    private RedemptionAdminDto toDto(Redemption r) {
        return toDto(r, false);
    }

    // includeDeliveryPoints en false para los listados (evita N+1 en
    // la tabla del admin, que no muestra esto por fila) — true solo
    // para el detalle puntual de un canje.
    private RedemptionAdminDto toDto(Redemption r, boolean includeDeliveryPoints) {
        RedemptionAdminDto dto = new RedemptionAdminDto();
        dto.setId(r.getId());
        dto.setPointsSpent(r.getPointsSpent());
        dto.setRedemptionDate(r.getRedemptionDate());
        dto.setStatus(r.getStatus());
        dto.setRedemptionCode(r.getRedemptionCode());
        dto.setExpiresAt(r.getExpiresAt());
        dto.setUsedAt(r.getUsedAt());
        dto.setExpired(r.isExpired());
        dto.setUsed(r.isUsed());
        dto.setDeliveryAddress(r.getDeliveryAddress());
        if (r.getChosenDeliveryPoint() != null) {
            dto.setChosenDeliveryPointId(r.getChosenDeliveryPoint().getId());
        }

        if (r.getUser() != null) {
            dto.setUser(new UserBasicDto(
                    r.getUser().getId(),
                    r.getUser().getName(),
                    r.getUser().getEmail(),
                    r.getUser().getDni(),
                    r.getUser().getPhone()
            ));
        }

        if (r.getReward() != null) {
            dto.setReward(new RewardBasicDto(
                    r.getReward().getId(),
                    r.getReward().getName(),
                    r.getReward().getPointsRequired(),
                    r.getReward().getImageUrl(),
                    r.getReward().getPartner(),
                    r.getReward().getWebsite(),
                    r.getReward().getDeliveryMethod()
            ));
        }

        if (includeDeliveryPoints) {
            dto.setDeliveryPoints(deliveryPointRepository.findByRedemptionIdOrderByDisplayOrderAsc(r.getId())
                    .stream()
                    .map(p -> new com.example.demo.application.dtos.DeliveryPointDto(
                            p.getId(), p.getLocationReference(), p.getScheduleInfo(), p.getDisplayOrder()))
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}