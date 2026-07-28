package com.example.demo.web.controllers;

import com.example.demo.application.dtos.RewardDto;
import com.example.demo.domain.reward.Reward;
import com.example.demo.domain.reward.RewardRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rewards")
public class RewardController {

    private final RewardRepository      rewardRepository;
    private final UserRepository        userRepository;
    private final com.example.demo.domain.reward.RewardImageRepository rewardImageRepository;

    public RewardController(RewardRepository rewardRepository,
                            UserRepository userRepository,
                            com.example.demo.domain.reward.RewardImageRepository rewardImageRepository) {
        this.rewardRepository      = rewardRepository;
        this.userRepository        = userRepository;
        this.rewardImageRepository = rewardImageRepository;
    }

    /**
     * Obtener todos los premios disponibles
     * GET /api/rewards
     */
    @GetMapping
    public ResponseEntity<List<RewardDto>> getAvailableRewards(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Reward> rewards = rewardRepository.findAvailableRewards(LocalDate.now());

        List<RewardDto> dtos = rewards.stream()
                .map(r -> toDto(r, user.getAvailablePoints()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Obtener todos los premios (incluyendo agotados) para mostrar catálogo completo
     * GET /api/rewards/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<RewardDto>> getAllRewards(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Reward> rewards = rewardRepository.findByActiveTrueAndDeletedFalseOrderByPointsRequiredAsc();

        List<RewardDto> dtos = rewards.stream()
                .map(r -> toDto(r, user.getAvailablePoints()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Obtener un premio puntual por id — usado por el carrusel del feed
     * y por cualquier otra pantalla que necesite resolver un id suelto.
     * GET /api/rewards/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getRewardById(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return rewardRepository.findById(id)
                .map(r -> ResponseEntity.ok((Object) toDto(r, user.getAvailablePoints())))
                .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(java.util.Map.of("error", "Premio no encontrado")));
    }

    // =============================================
    // HELPER
    // =============================================
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
        dto.setCanRedeem(r.isAvailable() && userPoints >= r.getPointsRequired());
        dto.setPartner(r.getPartner());
        dto.setWebsite(r.getWebsite());
        dto.setDiscountValue(r.getDiscountValue());
        dto.setDiscountType(r.getDiscountType());
        dto.setDiscountCode(r.getDiscountCode());
        dto.setDiscountChannel(r.getDiscountChannel());
        dto.setMinimumPurchase(r.getMinimumPurchase());
        dto.setApplicableProducts(r.getApplicableProducts());
        dto.setStackable(r.getStackable());
        dto.setExperienceType(r.getExperienceType());
        dto.setLocation(r.getLocation());
        dto.setEventDate(r.getEventDate());
        dto.setMaxCapacity(r.getMaxCapacity());
        dto.setDuration(r.getDuration());
        dto.setIncludesTransport(r.getIncludesTransport());
        dto.setRequirements(r.getRequirements());
        dto.setCompanionAllowed(r.getCompanionAllowed());
        dto.setBrand(r.getBrand());
        dto.setMaterial(r.getMaterial());
        dto.setColor(r.getColor());
        dto.setSize(r.getSize());
        dto.setDimensions(r.getDimensions());
        dto.setWeight(r.getWeight());
        dto.setOrigin(r.getOrigin());
        dto.setUnitsIncluded(r.getUnitsIncluded());
        dto.setCondition(r.getCondition());
        dto.setCinemaChain(r.getCinemaChain());
        dto.setCinemaFormat(r.getCinemaFormat());
        dto.setCinemaRestrictions(r.getCinemaRestrictions());
        dto.setTicketsIncluded(r.getTicketsIncluded());
        dto.setIncludesSnack(r.getIncludesSnack());

        // Entrega (comunes)
        dto.setDeliveryMethod(r.getDeliveryMethod());
        dto.setPickupPoint(r.getPickupPoint());
        dto.setDeliveryCost(r.getDeliveryCost());

        // Descuento extra
        dto.setRedeemMethod(r.getRedeemMethod());

        // Experiencia extra
        dto.setRequiresConfirmation(r.getRequiresConfirmation());
        dto.setTransferable(r.getTransferable());
        dto.setOrganizer(r.getOrganizer());

        // Cargar imágenes
        var images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(r.getId(), "COMMON");
        dto.setImages(images.stream().map(img -> {
            RewardDto.ImageDto imgDto = new RewardDto.ImageDto();
            imgDto.setId(img.getId());
            imgDto.setImageUrl(img.getImageUrl());
            imgDto.setPrimary(img.isPrimary());
            return imgDto;
        }).collect(java.util.stream.Collectors.toList()));

        return dto;
    }

    /**
     * Versión pública (sin auth) de un premio puntual — para el link
     * compartido. No expone canRedeem ni nada personal del usuario.
     * GET /api/public/rewards/{id}
     */
    @GetMapping("/public/{id}")
    public ResponseEntity<?> getRewardPublic(@PathVariable Long id) {
        return rewardRepository.findById(id)
                .map(r -> {
                    RewardDto full = toDto(r, 0);
                    com.example.demo.application.dtos.RewardPublicDto dto = new com.example.demo.application.dtos.RewardPublicDto();
                    dto.setId(full.getId());
                    dto.setName(full.getName());
                    dto.setDescription(full.getDescription());
                    dto.setImageUrl(full.getImageUrl());
                    dto.setPointsRequired(full.getPointsRequired());
                    dto.setTipo("COMUN");
                    return ResponseEntity.ok((Object) dto);
                })
                .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(java.util.Map.of("error", "Premio no encontrado")));
    }
}