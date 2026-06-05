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
}