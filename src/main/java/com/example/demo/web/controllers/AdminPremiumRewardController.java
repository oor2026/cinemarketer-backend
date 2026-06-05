package com.example.demo.web.controllers;

import com.example.demo.application.services.PremiumRewardService;
import com.example.demo.domain.premium.*;
import com.example.demo.domain.reward.RewardImage;
import com.example.demo.domain.reward.RewardImageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.example.demo.application.services.CloudinaryService;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/premium/rewards")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminPremiumRewardController {

    private final PremiumRewardRepository  premiumRewardRepository;
    private final PremiumRewardService     premiumRewardService;
    private final PremiumDrawEntryRepository drawEntryRepository;
    private final CloudinaryService        cloudinaryService;
    private final RewardImageRepository    rewardImageRepository;

    public AdminPremiumRewardController(PremiumRewardRepository premiumRewardRepository,
                                        PremiumRewardService premiumRewardService,
                                        PremiumDrawEntryRepository drawEntryRepository,
                                        CloudinaryService cloudinaryService,
                                        RewardImageRepository rewardImageRepository) {
        this.premiumRewardRepository = premiumRewardRepository;
        this.premiumRewardService    = premiumRewardService;
        this.drawEntryRepository     = drawEntryRepository;
        this.cloudinaryService       = cloudinaryService;
        this.rewardImageRepository   = rewardImageRepository;
    }

    // =============================================
    // CRUD PREMIOS PREMIUM
    // =============================================

    @GetMapping
    public ResponseEntity<List<PremiumReward>> getAll() {
        return ResponseEntity.ok(premiumRewardRepository.findByDeletedFalse());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return premiumRewardRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            PremiumReward reward = new PremiumReward();
            reward.setName((String) body.get("name"));
            reward.setDescription((String) body.get("description"));
            reward.setImageUrl((String) body.get("imageUrl"));
            reward.setType(PremiumRewardType.valueOf((String) body.get("type")));
            reward.setPartner((String) body.get("partner"));
            reward.setWebsite((String) body.get("website"));
            reward.setTermsConditions((String) body.get("termsConditions"));
            reward.setPointsRequired(body.get("pointsRequired") != null
                    ? ((Number) body.get("pointsRequired")).intValue() : 0);
            reward.setStock(body.get("stock") != null
                    ? ((Number) body.get("stock")).intValue() : null);
            if (body.get("drawDate") != null) {
                reward.setDrawDate(LocalDateTime.parse((String) body.get("drawDate")));
            }
            reward.setActive(true);
            PremiumReward saved = premiumRewardRepository.save(reward);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        try {
            PremiumReward reward = opt.get();
            if (body.get("name") != null)             reward.setName((String) body.get("name"));
            if (body.get("description") != null)      reward.setDescription((String) body.get("description"));
            if (body.get("imageUrl") != null)         reward.setImageUrl((String) body.get("imageUrl"));
            if (body.get("partner") != null)          reward.setPartner((String) body.get("partner"));
            if (body.get("website") != null)          reward.setWebsite((String) body.get("website"));
            if (body.get("termsConditions") != null)  reward.setTermsConditions((String) body.get("termsConditions"));
            if (body.get("pointsRequired") != null)
                reward.setPointsRequired(((Number) body.get("pointsRequired")).intValue());
            if (body.get("stock") != null)
                reward.setStock(((Number) body.get("stock")).intValue());
            if (body.get("drawDate") != null)
                reward.setDrawDate(LocalDateTime.parse((String) body.get("drawDate")));
            if (body.get("active") != null)
                reward.setActive((Boolean) body.get("active"));

            return ResponseEntity.ok(premiumRewardRepository.save(reward));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PremiumReward reward = opt.get();
        reward.setActive(false);
        premiumRewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio desactivado correctamente"));
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PremiumReward reward = opt.get();
        reward.setDeleted(true);
        premiumRewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio eliminado correctamente"));
    }

    // =============================================
    // SORTEOS
    // =============================================

    @GetMapping("/{id}/entries")
    public ResponseEntity<?> getEntries(@PathVariable Long id) {
        List<PremiumDrawEntry> entries = drawEntryRepository.findByRewardId(id);
        return ResponseEntity.ok(Map.of(
                "rewardId",     id,
                "totalEntries", entries.size(),
                "entries", entries.stream().map(e -> Map.of(
                        "userId",    e.getUser().getId(),
                        "userName",  e.getUser().getName(),
                        "userEmail", e.getUser().getEmail(),
                        "enteredAt", e.getEnteredAt()
                )).toList()
        ));
    }

    @PostMapping("/{id}/draw")
    public ResponseEntity<?> executeDraw(@PathVariable Long id) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PremiumReward reward = opt.get();
        if (reward.getType() != PremiumRewardType.SORTEO) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Este premio no es un sorteo"));
        }
        if (reward.isDrawExecuted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El sorteo ya fue ejecutado"));
        }
        try {
            return ResponseEntity.ok(premiumRewardService.executeDraw(reward));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // =============================================
    // GESTIÓN DE IMÁGENES
    // =============================================

    /**
     * Listar imágenes de un premio premium
     * GET /api/admin/premium/rewards/{id}/images
     */
    @GetMapping("/{id}/images")
    public ResponseEntity<?> getImages(@PathVariable Long id) {
        List<RewardImage> images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "PREMIUM");
        return ResponseEntity.ok(images);
    }

    /**
     * Subir nueva imagen a un premio premium (máx 5)
     * POST /api/admin/premium/rewards/{id}/images
     */
    @PostMapping("/{id}/images")
    @Transactional
    public ResponseEntity<?> addImage(@PathVariable Long id,
                                      @RequestParam("image") MultipartFile file) {
        PremiumReward reward = premiumRewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        long count = rewardImageRepository.countByRewardIdAndRewardType(id, "PREMIUM");
        if (count >= 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Máximo 5 imágenes por premio"));
        }

        try {
            String imageUrl = cloudinaryService.uploadImage(file, "cinemarketer/rewards/premium");

            RewardImage img = new RewardImage();
            img.setRewardId(id);
            img.setRewardType("PREMIUM");
            img.setImageUrl(imageUrl);
            img.setPrimary(count == 0);
            rewardImageRepository.save(img);

            if (count == 0) {
                reward.setImageUrl(imageUrl);
                premiumRewardRepository.save(reward);
            }

            return ResponseEntity.ok(Map.of(
                    "id",        img.getId(),
                    "imageUrl",  imageUrl,
                    "isPrimary", img.isPrimary()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al subir imagen: " + e.getMessage()));
        }
    }

    /**
     * Marcar imagen como principal
     * PATCH /api/admin/premium/rewards/{id}/images/{imageId}/primary
     */
    @PatchMapping("/{id}/images/{imageId}/primary")
    @Transactional
    public ResponseEntity<?> setPrimaryImage(@PathVariable Long id,
                                             @PathVariable Long imageId) {
        List<RewardImage> images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "PREMIUM");
        images.forEach(img -> img.setPrimary(false));
        rewardImageRepository.saveAll(images);

        RewardImage img = rewardImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Imagen no encontrada"));
        img.setPrimary(true);
        rewardImageRepository.save(img);

        PremiumReward reward = premiumRewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));
        reward.setImageUrl(img.getImageUrl());
        premiumRewardRepository.save(reward);

        return ResponseEntity.ok(Map.of("message", "Imagen principal actualizada"));
    }

    /**
     * Eliminar imagen individual
     * DELETE /api/admin/premium/rewards/{id}/images/{imageId}
     */
    @DeleteMapping("/{id}/images/{imageId}")
    @Transactional
    public ResponseEntity<?> deleteImage(@PathVariable Long id,
                                         @PathVariable Long imageId) {
        RewardImage img = rewardImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Imagen no encontrada"));

        try { cloudinaryService.deleteImage(img.getImageUrl()); } catch (Exception ignored) {}

        boolean eraPrincipal = img.isPrimary();
        rewardImageRepository.delete(img);

        if (eraPrincipal) {
            List<RewardImage> restantes = rewardImageRepository
                    .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "PREMIUM");
            PremiumReward reward = premiumRewardRepository.findById(id).orElse(null);
            if (!restantes.isEmpty()) {
                restantes.get(0).setPrimary(true);
                rewardImageRepository.save(restantes.get(0));
                if (reward != null) {
                    reward.setImageUrl(restantes.get(0).getImageUrl());
                    premiumRewardRepository.save(reward);
                }
            } else if (reward != null) {
                reward.setImageUrl(null);
                premiumRewardRepository.save(reward);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Imagen eliminada correctamente"));
    }

    /**
     * Endpoint legacy — subir imagen única
     * POST /api/admin/premium/rewards/{id}/image
     */
    @PostMapping("/{id}/image")
    @Transactional
    public ResponseEntity<?> uploadImageLegacy(@PathVariable Long id,
                                               @RequestParam("image") MultipartFile file) {
        return addImage(id, file);
    }
}