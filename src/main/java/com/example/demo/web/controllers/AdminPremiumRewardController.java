package com.example.demo.web.controllers;

import com.example.demo.application.services.PremiumRewardService;
import com.example.demo.domain.premium.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final PremiumRewardRepository premiumRewardRepository;
    private final PremiumRewardService premiumRewardService;
    private final PremiumDrawEntryRepository drawEntryRepository;
    private final CloudinaryService cloudinaryService;

    public AdminPremiumRewardController(PremiumRewardRepository premiumRewardRepository,
                                        PremiumRewardService premiumRewardService,
                                        PremiumDrawEntryRepository drawEntryRepository,
                                        CloudinaryService cloudinaryService) {
        this.premiumRewardRepository = premiumRewardRepository;
        this.premiumRewardService = premiumRewardService;
        this.drawEntryRepository = drawEntryRepository;
        this.cloudinaryService = cloudinaryService;
    }

    /**
     * GET /api/admin/premium/rewards
     * Lista todos los premios/sorteos premium
     */
    @GetMapping
    public ResponseEntity<List<PremiumReward>> getAll() {
        return ResponseEntity.ok(premiumRewardRepository.findByDeletedFalse());
    }

    /**
     * GET /api/admin/premium/rewards/{id}
     * Detalle de un premio/sorteo
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return premiumRewardRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/admin/premium/rewards
     * Crear nuevo premio o sorteo premium
     */
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

    /**
     * PUT /api/admin/premium/rewards/{id}
     * Editar premio/sorteo premium
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        try {
            PremiumReward reward = opt.get();
            if (body.get("name") != null) reward.setName((String) body.get("name"));
            if (body.get("description") != null) reward.setDescription((String) body.get("description"));
            if (body.get("imageUrl") != null) reward.setImageUrl((String) body.get("imageUrl"));
            if (body.get("partner") != null) reward.setPartner((String) body.get("partner"));
            if (body.get("website") != null) reward.setWebsite((String) body.get("website"));
            if (body.get("termsConditions") != null) reward.setTermsConditions((String) body.get("termsConditions"));
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

    /**
     * DELETE /api/admin/premium/rewards/{id}
     * Desactivar (soft delete) un premio/sorteo
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PremiumReward reward = opt.get();
        reward.setActive(false);
        premiumRewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio desactivado correctamente"));
    }

    /**
     * DELETE /api/admin/premium/rewards/{id}/delete
     * Borrado lógico
     */
    @DeleteMapping("/{id}/delete")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PremiumReward reward = opt.get();
        reward.setDeleted(true);
        premiumRewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio eliminado correctamente"));
    }

    /**
     * GET /api/admin/premium/rewards/{id}/entries
     * Ver participantes anotados en un sorteo
     */
    @GetMapping("/{id}/entries")
    public ResponseEntity<?> getEntries(@PathVariable Long id) {
        List<PremiumDrawEntry> entries = drawEntryRepository.findByRewardId(id);
        return ResponseEntity.ok(Map.of(
                "rewardId", id,
                "totalEntries", entries.size(),
                "entries", entries.stream().map(e -> Map.of(
                        "userId", e.getUser().getId(),
                        "userName", e.getUser().getName(),
                        "userEmail", e.getUser().getEmail(),
                        "enteredAt", e.getEnteredAt()
                )).toList()
        ));
    }

    /**
     * POST /api/admin/premium/rewards/{id}/draw
     * Ejecutar el sorteo manualmente — selecciona ganador aleatorio
     */
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
            Map<String, Object> result = premiumRewardService.executeDraw(reward);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/premium/rewards/{id}/image
     * Subir imagen a Cloudinary y actualizar el premio
     */
    @PostMapping("/{id}/image")
    public ResponseEntity<?> uploadImage(@PathVariable Long id,
                                         @RequestParam("image") MultipartFile file) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        try {
            String imageUrl = cloudinaryService.uploadImage(file, "premium-rewards");
            PremiumReward reward = opt.get();
            reward.setImageUrl(imageUrl);
            premiumRewardRepository.save(reward);
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al subir la imagen: " + e.getMessage()));
        }
    }
}
