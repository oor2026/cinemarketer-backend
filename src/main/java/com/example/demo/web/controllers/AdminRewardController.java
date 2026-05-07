package com.example.demo.web.controllers;

import com.example.demo.application.dtos.AdminRewardRequest;
import com.example.demo.application.dtos.RewardDto;
import com.example.demo.application.services.CloudinaryService;
import com.example.demo.domain.reward.Reward;
import com.example.demo.domain.reward.RewardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/rewards")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminRewardController {

    private final RewardRepository rewardRepository;
    private final CloudinaryService cloudinaryService;

    public AdminRewardController(RewardRepository rewardRepository,
                                 CloudinaryService cloudinaryService) {
        this.rewardRepository = rewardRepository;
        this.cloudinaryService = cloudinaryService;
    }

    /**
     * Listar todos los premios (activos e inactivos)
     * GET /api/admin/rewards
     */
    @GetMapping
    public ResponseEntity<List<RewardDto>> getAllRewards() {
        List<Reward> rewards = rewardRepository.findByDeletedFalse();
        List<RewardDto> dtos = rewards.stream()
                .map(r -> toDto(r, 0))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Crear nuevo premio
     * POST /api/admin/rewards
     */
    @PostMapping
    @Transactional
    public ResponseEntity<RewardDto> createReward(@RequestBody AdminRewardRequest request) {
        Reward reward = new Reward();
        mapRequestToReward(request, reward);
        rewardRepository.save(reward);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(reward, 0));
    }

    /**
     * Editar premio existente
     * PUT /api/admin/rewards/{id}
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<RewardDto> updateReward(@PathVariable Long id,
                                                   @RequestBody AdminRewardRequest request) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        mapRequestToReward(request, reward);
        rewardRepository.save(reward);
        return ResponseEntity.ok(toDto(reward, 0));
    }

    /**
     * Subir imagen de un premio
     * POST /api/admin/rewards/{id}/image
     */
    @PostMapping("/{id}/image")
    @Transactional
    public ResponseEntity<?> uploadImage(@PathVariable Long id,
                                          @RequestParam("image") MultipartFile file) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        try {
            // Eliminar imagen anterior si existe
            if (reward.getImageUrl() != null) {
                cloudinaryService.deleteImage(reward.getImageUrl());
            }

            // Subir nueva imagen a Cloudinary
            String imageUrl = cloudinaryService.uploadImage(file, "cinemarketer/rewards");
            reward.setImageUrl(imageUrl);
            rewardRepository.save(reward);

            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al subir la imagen: " + e.getMessage()));
        }
    }

    /**
     * Desactivar premio (soft delete)
     * DELETE /api/admin/rewards/{id}
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deactivateReward(@PathVariable Long id) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        reward.setActive(false);
        rewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio desactivado correctamente"));
    }

    /**
     * Borrado lógico (ocultar para siempre del front)
     * DELETE /api/admin/rewards/{id}/delete
     */
    @DeleteMapping("/{id}/delete")
    @Transactional
    public ResponseEntity<?> deleteReward(@PathVariable Long id) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        reward.setDeleted(true);
        rewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio eliminado correctamente"));
    }

    /**
     * Reactivar premio
     * PATCH /api/admin/rewards/{id}/activate
     */
    @PatchMapping("/{id}/activate")
    @Transactional
    public ResponseEntity<?> activateReward(@PathVariable Long id) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        reward.setActive(true);
        rewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio activado correctamente"));
    }

    // =============================================
    // HELPERS
    // =============================================
    private void mapRequestToReward(AdminRewardRequest req, Reward reward) {
        reward.setName(req.getName());
        reward.setDescription(req.getDescription());
        reward.setRewardType(req.getRewardType());
        reward.setPointsRequired(req.getPointsRequired());
        reward.setStock(req.getStock());
        reward.setInitialStock(req.getStock());
        reward.setExpiryDate(req.getExpiryDate());
        reward.setTermsConditions(req.getTermsConditions());
        reward.setActive(req.getActive() != null ? req.getActive() : true);
        reward.setPartner(req.getPartner());
        reward.setWebsite(req.getWebsite());
    }

    private RewardDto toDto(Reward r, int userPoints) {
        RewardDto dto = new RewardDto();
        dto.setId(r.getId());
        dto.setName(r.getName());
        dto.setDescription(r.getDescription());
        dto.setRewardType(r.getRewardType());
        dto.setPointsRequired(r.getPointsRequired());
        dto.setStock(r.getStock());
        dto.setImageUrl(r.getImageUrl());
        dto.setExpiryDate(r.getExpiryDate());
        dto.setTermsConditions(r.getTermsConditions());
        dto.setActive(r.getActive());
        dto.setHasStock(r.hasStock());
        dto.setIsExpired(r.isExpired());
        dto.setCanRedeem(false);
        dto.setPartner(r.getPartner());
        dto.setWebsite(r.getWebsite());
        return dto;
    }
}
