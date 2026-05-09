package com.example.demo.web.controllers;

import com.example.demo.application.dtos.RedeemRequest;
import com.example.demo.application.dtos.RedemptionDto;
import com.example.demo.application.services.EmailService;
import com.example.demo.application.services.LevelCalculatorService;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.pointconfig.PointAction;
import com.example.demo.domain.redemption.Redemption;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.redemption.RedemptionStatus;
import com.example.demo.domain.reward.Reward;
import com.example.demo.domain.reward.RewardRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/redemptions")
public class RedemptionController {

    private static final Logger log = LoggerFactory.getLogger(RedemptionController.class);

    private final RedemptionRepository redemptionRepository;
    private final RewardRepository rewardRepository;
    private final UserRepository userRepository;
    private final PointTransactionService pointTransactionService;
    private final LevelCalculatorService levelCalculatorService;
    private final EmailService emailService;

    public RedemptionController(RedemptionRepository redemptionRepository,
                                RewardRepository rewardRepository,
                                UserRepository userRepository,
                                PointTransactionService pointTransactionService,
                                LevelCalculatorService levelCalculatorService,
                                EmailService emailService) {
        this.redemptionRepository = redemptionRepository;
        this.rewardRepository = rewardRepository;
        this.userRepository = userRepository;
        this.pointTransactionService = pointTransactionService;
        this.levelCalculatorService = levelCalculatorService;
        this.emailService = emailService;
    }

    @GetMapping("/me")
    public ResponseEntity<List<RedemptionDto>> getMyRedemptions(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Redemption> redemptions = redemptionRepository
                .findByUserIdOrderByRedemptionDateDesc(user.getId());

        List<RedemptionDto> dtos = redemptions.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> redeemReward(
            @RequestBody RedeemRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Reward reward = rewardRepository.findById(request.getRewardId())
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        if (!reward.isAvailable()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El premio no está disponible"));
        }

        if (user.getTotalPoints() < reward.getPointsRequired()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Puntos insuficientes",
                            "required", reward.getPointsRequired(),
                            "available", user.getTotalPoints()));
        }

        // Descontar puntos al usuario
        user.subtractPoints(reward.getPointsRequired());
        userRepository.save(user);

        // Reducir stock del premio
        reward.decreaseStock();
        rewardRepository.save(reward);

        // Generar código único de canje
        String code = "CINE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Crear registro de canje
        Redemption redemption = new Redemption();
        redemption.setUser(user);
        redemption.setReward(reward);
        redemption.setPointsSpent(reward.getPointsRequired());
        redemption.setStatus(RedemptionStatus.PENDING);
        redemption.setRedemptionCode(code);
        redemption.setExpiresAt(LocalDateTime.now().plusDays(30));
        redemptionRepository.save(redemption);

        // Registrar transacción de puntos gastados
        pointTransactionService.registerSpent(
                user,
                PointAction.REWARD_REDEMPTION,
                reward.getPointsRequired(),
                reward.getId(),
                "Canje: " + reward.getName()
        );

        // Recalcular nivel
        UserLevel oldLevel = user.getLevel();
        UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);
        if (oldLevel != newLevel) {
            user.setLevel(newLevel);
            user.setLevelUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }

        // Disparar mail de confirmación de canje
        try {
            emailService.sendRedemptionConfirmationEmail(
                    user.getEmail(),
                    user.getName(),
                    reward.getName(),
                    code
            );
        } catch (Exception e) {
            log.warn("No se pudo enviar mail de confirmación de canje: {}", e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(redemption));
    }

    private RedemptionDto toDto(Redemption r) {
        RedemptionDto dto = new RedemptionDto();
        dto.setId(r.getId());
        dto.setRewardId(r.getReward().getId());
        dto.setRewardName(r.getReward().getName());
        dto.setRewardDescription(r.getReward().getDescription());
        dto.setRewardImageUrl(r.getReward().getImageUrl());
        dto.setPointsSpent(r.getPointsSpent());
        dto.setStatus(r.getStatus());
        dto.setRedemptionCode(r.getRedemptionCode());
        dto.setRedemptionDate(r.getRedemptionDate());
        dto.setExpiresAt(r.getExpiresAt());
        dto.setUsedAt(r.getUsedAt());
        return dto;
    }
}