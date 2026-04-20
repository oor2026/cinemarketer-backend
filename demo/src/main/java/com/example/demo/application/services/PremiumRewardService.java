package com.example.demo.application.services;

import com.example.demo.application.dtos.PremiumRewardDto;
import com.example.demo.domain.premium.*;
import com.example.demo.domain.support.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PremiumRewardService {

    private static final Logger log = LoggerFactory.getLogger(PremiumRewardService.class);

    private final PremiumRewardRepository premiumRewardRepository;
    private final PremiumDrawEntryRepository drawEntryRepository;
    private final PremiumRedemptionRepository premiumRedemptionRepository;
    private final UserRepository userRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;

    public PremiumRewardService(PremiumRewardRepository premiumRewardRepository,
                                PremiumDrawEntryRepository drawEntryRepository,
                                PremiumRedemptionRepository premiumRedemptionRepository,
                                UserRepository userRepository,
                                SupportTicketRepository supportTicketRepository,
                                SupportMessageRepository supportMessageRepository) {
        this.premiumRewardRepository = premiumRewardRepository;
        this.drawEntryRepository = drawEntryRepository;
        this.premiumRedemptionRepository = premiumRedemptionRepository;
        this.userRepository = userRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.supportMessageRepository = supportMessageRepository;
    }

    // ==============================================
    // CATÁLOGO
    // ==============================================

    public List<PremiumRewardDto> getCatalog(User user, boolean isPremium, PremiumRewardType type) {
        List<PremiumReward> rewards = type != null
                ? premiumRewardRepository.findByActiveTrueAndType(type)
                : premiumRewardRepository.findByActiveTrue();

        return rewards.stream()
                .map(r -> toDto(r, user, isPremium))
                .collect(Collectors.toList());
    }

    // ==============================================
    // CANJEAR PREMIO (CANJEABLE)
    // ==============================================

    @Transactional
    public Map<String, Object> redeemReward(User user, Long rewardId) {
        PremiumReward reward = premiumRewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalStateException("Premio no encontrado"));

        if (reward.getType() != PremiumRewardType.CANJEABLE) {
            throw new IllegalStateException("Este ítem es un sorteo, no un premio canjeable");
        }
        if (!reward.isActive()) {
            throw new IllegalStateException("El premio no está disponible");
        }
        if (!reward.hasStock()) {
            throw new IllegalStateException("El premio está agotado");
        }
        if (user.getTotalPoints() < reward.getPointsRequired()) {
            throw new IllegalStateException("Puntos insuficientes para canjear este premio");
        }
        if (premiumRedemptionRepository.existsByRewardIdAndUserId(rewardId, user.getId())) {
            throw new IllegalStateException("Ya canjeaste este premio");
        }

        // Descontar puntos
        user.subtractPoints(reward.getPointsRequired());
        userRepository.save(user);

        // Reducir stock
        reward.decreaseStock();
        premiumRewardRepository.save(reward);

        // Registrar canje
        String code = "PREM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PremiumRedemption redemption = new PremiumRedemption();
        redemption.setReward(reward);
        redemption.setUser(user);
        redemption.setPointsSpent(reward.getPointsRequired());
        redemption.setRedemptionCode(code);
        redemption.setStatus(PremiumRedemptionStatus.PENDING);
        premiumRedemptionRepository.save(redemption);

        log.info("✅ Premio premium canjeado: {} por usuario: {}", reward.getName(), user.getEmail());

        return Map.of(
                "redemptionCode", code,
                "rewardName", reward.getName(),
                "pointsSpent", reward.getPointsRequired()
        );
    }

    // ==============================================
    // ANOTARSE EN SORTEO
    // ==============================================

    @Transactional
    public void enterDraw(User user, Long rewardId) {
        PremiumReward reward = premiumRewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalStateException("Sorteo no encontrado"));

        if (reward.getType() != PremiumRewardType.SORTEO) {
            throw new IllegalStateException("Este ítem no es un sorteo");
        }
        if (!reward.isActive()) {
            throw new IllegalStateException("El sorteo no está disponible");
        }
        if (reward.isDrawExecuted()) {
            throw new IllegalStateException("Este sorteo ya fue ejecutado");
        }
        if (drawEntryRepository.existsByRewardIdAndUserId(rewardId, user.getId())) {
            throw new IllegalStateException("Ya estás anotado en este sorteo");
        }

        PremiumDrawEntry entry = new PremiumDrawEntry();
        entry.setReward(reward);
        entry.setUser(user);
        drawEntryRepository.save(entry);

        log.info("✅ Usuario {} anotado en sorteo: {}", user.getEmail(), reward.getName());
    }

    // ==============================================
    // EJECUTAR SORTEO (ADMIN)
    // ==============================================

    @Transactional
    public Map<String, Object> executeDraw(PremiumReward reward) {
        List<PremiumDrawEntry> entries = drawEntryRepository.findByRewardId(reward.getId());

        if (entries.isEmpty()) {
            throw new IllegalStateException("No hay participantes anotados en este sorteo");
        }

        // Selección aleatoria del ganador
        int winnerIndex = new Random().nextInt(entries.size());
        PremiumDrawEntry winnerEntry = entries.get(winnerIndex);
        User winner = winnerEntry.getUser();

        // Registrar ganador
        reward.setWinner(winner);
        reward.setDrawExecuted(true);
        reward.setDrawDate(LocalDateTime.now());
        premiumRewardRepository.save(reward);

        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUser(winner);
            ticket.setSubject("🏆 ¡Ganaste el sorteo: " + reward.getName() + "!");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            SupportMessage message = new SupportMessage();
            message.setTicket(savedTicket);
            message.setSenderType(SenderType.ADMIN);
            message.setSenderName("Cinemarketer");
            ticket.setSubject("¡Ganaste el sorteo: " + reward.getName() + "!");
            message.setContent("¡Felicitaciones " + winner.getName() + "!\n\n" +
                    "Sos el ganador del sorteo \"" + reward.getName() + "\".\n\n" +
                    "Necesitamos que nos confirmes la recepción de este mensaje., luego nuestro equipo se va a contactar con vos a la brevedad para coordinar " +
                    "la entrega del premio. Podés acercarnos cualquier otra consulta.\n\n" +
                    "Equipo Cinemarketer.");
            message.setReadByAdmin(true);
            message.setReadByUser(false);
            supportMessageRepository.save(message);

            log.info("📩 Notificación de sorteo enviada al ganador: {}", winner.getEmail());
        } catch (Exception e) {
            log.warn("⚠️ No se pudo enviar notificación al ganador: {}", e.getMessage());
        }

        log.info("🏆 Sorteo ejecutado: {} — Ganador: {}", reward.getName(), winner.getEmail());

        return Map.of(
                "rewardId", reward.getId(),
                "rewardName", reward.getName(),
                "totalParticipants", entries.size(),
                "winnerId", winner.getId(),
                "winnerName", winner.getName(),
                "winnerEmail", winner.getEmail(),
                "executedAt", LocalDateTime.now().toString()
        );
    }

    // ==============================================
    // CONVERSIÓN A DTO
    // ==============================================

    private PremiumRewardDto toDto(PremiumReward reward, User user, boolean isPremium) {
        PremiumRewardDto dto = new PremiumRewardDto();
        dto.setId(reward.getId());
        dto.setName(reward.getName());
        dto.setDescription(reward.getDescription());
        dto.setImageUrl(reward.getImageUrl());
        dto.setType(reward.getType());
        dto.setPointsRequired(reward.getPointsRequired());
        dto.setStock(reward.getStock());
        dto.setDrawDate(reward.getDrawDate());
        dto.setDrawExecuted(reward.isDrawExecuted());
        dto.setActive(reward.isActive());
        dto.setUserIsPremium(isPremium);
        dto.setPartner(reward.getPartner());
        dto.setWebsite(reward.getWebsite());
        dto.setTermsConditions(reward.getTermsConditions());

        if (reward.getWinner() != null) {
            dto.setWinnerName(reward.getWinner().getName());
        }

        // Campos calculados según el usuario
        if (reward.getType() == PremiumRewardType.CANJEABLE) {
            dto.setCanRedeem(user.isActivePremium()
                    && reward.hasStock()
                    && user.getTotalPoints() >= reward.getPointsRequired()
                    && !premiumRedemptionRepository.existsByRewardIdAndUserId(reward.getId(), user.getId()));
        } else if (reward.getType() == PremiumRewardType.SORTEO) {
            dto.setAlreadyEntered(drawEntryRepository.existsByRewardIdAndUserId(reward.getId(), user.getId()));
            dto.setTotalEntries(drawEntryRepository.countByRewardId(reward.getId()));
        }

        return dto;
    }
}
