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
    private final EmailService emailService;

    public AdminRedemptionController(RedemptionRepository redemptionRepository,
                                     EmailService emailService) {
        this.redemptionRepository = redemptionRepository;
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
        return ResponseEntity.ok(toDto(redemption));
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
                    r.getReward().getWebsite()
            ));
        }

        return dto;
    }
}