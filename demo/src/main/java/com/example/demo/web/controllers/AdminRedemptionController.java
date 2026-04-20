package com.example.demo.web.controllers;

import com.example.demo.application.dtos.RedemptionAdminDto;
import com.example.demo.application.dtos.UserBasicDto;
import com.example.demo.application.dtos.RewardBasicDto;
import com.example.demo.domain.redemption.Redemption;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.redemption.RedemptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/redemptions")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminRedemptionController {

    private final RedemptionRepository redemptionRepository;

    public AdminRedemptionController(RedemptionRepository redemptionRepository) {
        this.redemptionRepository = redemptionRepository;
    }

    /**
     * Obtener todos los canjes (con paginación)
     * GET /api/admin/redemptions?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRedemptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Page<Redemption> pageResult = redemptionRepository.findAll(
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

    /**
     * Obtener canjes por estado
     * GET /api/admin/redemptions/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<RedemptionAdminDto>> getRedemptionsByStatus(
            @PathVariable RedemptionStatus status) {

        List<Redemption> redemptions = redemptionRepository.findByStatus(status);
        List<RedemptionAdminDto> dtos = redemptions.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Obtener detalles de un canje específico
     * GET /api/admin/redemptions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<RedemptionAdminDto> getRedemptionById(@PathVariable Long id) {
        Redemption redemption = redemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje no encontrado"));
        return ResponseEntity.ok(toDto(redemption));
    }

    /**
     * Actualizar estado de un canje
     * PATCH /api/admin/redemptions/{id}/status
     */
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
        }

        return ResponseEntity.ok(toDto(redemption));
    }

    // =============================================
    // HELPER
    // =============================================
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

        // Datos del usuario
        if (r.getUser() != null) {
            dto.setUser(new UserBasicDto(
                    r.getUser().getId(),
                    r.getUser().getName(),
                    r.getUser().getEmail(),
                    r.getUser().getDni(),
                    r.getUser().getPhone()
            ));
        }

        // Datos del premio
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