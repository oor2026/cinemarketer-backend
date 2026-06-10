package com.example.demo.web.controllers;

import com.example.demo.application.dtos.AdminRewardRequest;
import com.example.demo.application.dtos.RewardDto;
import com.example.demo.application.services.CloudinaryService;
import com.example.demo.domain.reward.Reward;
import com.example.demo.domain.reward.RewardImage;
import com.example.demo.domain.reward.RewardImageRepository;
import com.example.demo.domain.reward.RewardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/rewards")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminRewardController {

    private final RewardRepository       rewardRepository;
    private final CloudinaryService      cloudinaryService;
    private final RewardImageRepository  rewardImageRepository;

    public AdminRewardController(RewardRepository rewardRepository,
                                 CloudinaryService cloudinaryService,
                                 RewardImageRepository rewardImageRepository) {
        this.rewardRepository      = rewardRepository;
        this.cloudinaryService     = cloudinaryService;
        this.rewardImageRepository = rewardImageRepository;
    }

    // =============================================
    // CRUD PREMIOS
    // =============================================

    @GetMapping
    public ResponseEntity<List<RewardDto>> getAllRewards() {
        List<Reward> rewards = rewardRepository.findByDeletedFalse();
        List<RewardDto> dtos = rewards.stream()
                .map(r -> toDto(r, 0))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<RewardDto> createReward(@RequestBody AdminRewardRequest request) {
        Reward reward = new Reward();
        mapRequestToReward(request, reward);
        rewardRepository.save(reward);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(reward, 0));
    }

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

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deactivateReward(@PathVariable Long id) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));
        reward.setActive(false);
        rewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio desactivado correctamente"));
    }

    @DeleteMapping("/{id}/delete")
    @Transactional
    public ResponseEntity<?> deleteReward(@PathVariable Long id) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));
        reward.setDeleted(true);
        rewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio eliminado correctamente"));
    }

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
    // GESTIÓN DE IMÁGENES
    // =============================================

    /**
     * Listar imágenes de un premio
     * GET /api/admin/rewards/{id}/images
     */
    @GetMapping("/{id}/images")
    public ResponseEntity<?> getImages(@PathVariable Long id) {
        List<RewardImage> images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "COMMON");
        return ResponseEntity.ok(images);
    }

    /**
     * Subir nueva imagen a un premio (máx 5)
     * POST /api/admin/rewards/{id}/images
     */
    @PostMapping("/{id}/images")
    @Transactional
    public ResponseEntity<?> addImage(@PathVariable Long id,
                                      @RequestParam("image") MultipartFile file) {
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        long count = rewardImageRepository.countByRewardIdAndRewardType(id, "COMMON");
        if (count >= 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Máximo 5 imágenes por premio"));
        }

        try {
            String imageUrl = cloudinaryService.uploadImage(file, "cinemarketer/rewards");

            RewardImage img = new RewardImage();
            img.setRewardId(id);
            img.setRewardType("COMMON");
            img.setImageUrl(imageUrl);
            img.setPrimary(count == 0); // primera imagen es principal por defecto
            rewardImageRepository.save(img);

            // Si es la primera, actualizar imageUrl del reward
            if (count == 0) {
                reward.setImageUrl(imageUrl);
                rewardRepository.save(reward);
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
     * PATCH /api/admin/rewards/{id}/images/{imageId}/primary
     */
    @PatchMapping("/{id}/images/{imageId}/primary")
    @Transactional
    public ResponseEntity<?> setPrimaryImage(@PathVariable Long id,
                                             @PathVariable Long imageId) {
        // Quitar principal actual
        List<RewardImage> images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "COMMON");
        images.forEach(img -> img.setPrimary(false));
        rewardImageRepository.saveAll(images);

        // Marcar nueva principal
        RewardImage img = rewardImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Imagen no encontrada"));
        img.setPrimary(true);
        rewardImageRepository.save(img);

        // Actualizar imageUrl del reward
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));
        reward.setImageUrl(img.getImageUrl());
        rewardRepository.save(reward);

        return ResponseEntity.ok(Map.of("message", "Imagen principal actualizada"));
    }

    /**
     * Eliminar imagen individual
     * DELETE /api/admin/rewards/{id}/images/{imageId}
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

        // Si era principal, asignar la siguiente como principal
        if (eraPrincipal) {
            List<RewardImage> restantes = rewardImageRepository
                    .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "COMMON");
            Reward reward = rewardRepository.findById(id).orElse(null);
            if (!restantes.isEmpty()) {
                restantes.get(0).setPrimary(true);
                rewardImageRepository.save(restantes.get(0));
                if (reward != null) {
                    reward.setImageUrl(restantes.get(0).getImageUrl());
                    rewardRepository.save(reward);
                }
            } else if (reward != null) {
                reward.setImageUrl(null);
                rewardRepository.save(reward);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Imagen eliminada correctamente"));
    }

    /**
     * Endpoint legacy — subir imagen única (mantener compatibilidad)
     * POST /api/admin/rewards/{id}/image
     */
    @PostMapping("/{id}/image")
    @Transactional
    public ResponseEntity<?> uploadImageLegacy(@PathVariable Long id,
                                               @RequestParam("image") MultipartFile file) {
        return addImage(id, file);
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
        reward.setDiscountValue(req.getDiscountValue());
        reward.setDiscountType(req.getDiscountType());
        reward.setExperienceType(req.getExperienceType());
        reward.setLocation(req.getLocation());
        reward.setEventDate(req.getEventDate());
        reward.setMaxCapacity(req.getMaxCapacity());
        reward.setDrawDate(req.getDrawDate());

        // Generar código de descuento automático para DESCUENTO
        if (req.getRewardType() != null &&
                req.getRewardType().name().equals("DESCUENTO") &&
                (reward.getDiscountCode() == null || reward.getDiscountCode().isEmpty())) {
            reward.setDiscountCode(generarCodigoDescuento());
        }

        // Merchandising
        reward.setBrand(req.getBrand());
        reward.setMaterial(req.getMaterial());
        reward.setColor(req.getColor());
        reward.setSize(req.getSize());
        reward.setDimensions(req.getDimensions());
        reward.setWeight(req.getWeight());
        reward.setOrigin(req.getOrigin());
        reward.setUnitsIncluded(req.getUnitsIncluded());
        reward.setCondition(req.getCondition());

        // Entrada de cine
        reward.setCinemaChain(req.getCinemaChain());
        reward.setCinemaFormat(req.getCinemaFormat());
        reward.setCinemaRestrictions(req.getCinemaRestrictions());
        reward.setTicketsIncluded(req.getTicketsIncluded());
        reward.setIncludesSnack(req.getIncludesSnack());

        // Descuento
        reward.setDiscountChannel(req.getDiscountChannel());
        reward.setMinimumPurchase(req.getMinimumPurchase());
        reward.setApplicableProducts(req.getApplicableProducts());
        reward.setStackable(req.getStackable());

        // Experiencia
        reward.setDuration(req.getDuration());
        reward.setIncludesTransport(req.getIncludesTransport());
        reward.setRequirements(req.getRequirements());
        reward.setCompanionAllowed(req.getCompanionAllowed());
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
        dto.setDiscountValue(r.getDiscountValue());
        dto.setDiscountType(r.getDiscountType());
        dto.setDiscountCode(r.getDiscountCode());
        dto.setExperienceType(r.getExperienceType());
        dto.setLocation(r.getLocation());
        dto.setEventDate(r.getEventDate());
        dto.setMaxCapacity(r.getMaxCapacity());
        dto.setDrawDate(r.getDrawDate());
        dto.setDrawExecuted(r.isDrawExecuted());

        // Merchandising
        dto.setBrand(r.getBrand());
        dto.setMaterial(r.getMaterial());
        dto.setColor(r.getColor());
        dto.setSize(r.getSize());
        dto.setDimensions(r.getDimensions());
        dto.setWeight(r.getWeight());
        dto.setOrigin(r.getOrigin());
        dto.setUnitsIncluded(r.getUnitsIncluded());
        dto.setCondition(r.getCondition());

        // Entrada de cine
        dto.setCinemaChain(r.getCinemaChain());
        dto.setCinemaFormat(r.getCinemaFormat());
        dto.setCinemaRestrictions(r.getCinemaRestrictions());
        dto.setTicketsIncluded(r.getTicketsIncluded());
        dto.setIncludesSnack(r.getIncludesSnack());

        // Descuento
        dto.setDiscountChannel(r.getDiscountChannel());
        dto.setMinimumPurchase(r.getMinimumPurchase());
        dto.setApplicableProducts(r.getApplicableProducts());
        dto.setStackable(r.getStackable());

        // Experiencia
        dto.setDuration(r.getDuration());
        dto.setIncludesTransport(r.getIncludesTransport());
        dto.setRequirements(r.getRequirements());
        dto.setCompanionAllowed(r.getCompanionAllowed());

        // Incluir lista de imágenes
        List<RewardImage> images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(r.getId(), "COMMON");
        dto.setImages(images.stream().map(img -> {
            RewardDto.ImageDto imgDto = new RewardDto.ImageDto();
            imgDto.setId(img.getId());
            imgDto.setImageUrl(img.getImageUrl());
            imgDto.setPrimary(img.isPrimary());
            return imgDto;
        }).collect(Collectors.toList()));

        return dto;
    }

    private String generarCodigoDescuento() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder("CINE-");
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}