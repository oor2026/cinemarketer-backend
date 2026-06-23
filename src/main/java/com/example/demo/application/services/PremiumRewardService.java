package com.example.demo.application.services;

import com.example.demo.application.dtos.PremiumRewardDto;
import com.example.demo.domain.notification.Notification;
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

    private final PremiumRewardRepository    premiumRewardRepository;
    private final PremiumDrawEntryRepository drawEntryRepository;
    private final PremiumRedemptionRepository premiumRedemptionRepository;
    private final UserRepository             userRepository;
    private final SupportTicketRepository    supportTicketRepository;
    private final SupportMessageRepository   supportMessageRepository;
    private final EmailService               emailService;
    private final com.example.demo.domain.reward.RewardImageRepository rewardImageRepository;
    private final com.example.demo.domain.notification.NotificationRepository notificationRepository;
    private final DrawResultRepository drawResultRepository;

    public PremiumRewardService(PremiumRewardRepository premiumRewardRepository,
                                PremiumDrawEntryRepository drawEntryRepository,
                                PremiumRedemptionRepository premiumRedemptionRepository,
                                UserRepository userRepository,
                                SupportTicketRepository supportTicketRepository,
                                SupportMessageRepository supportMessageRepository,
                                EmailService emailService,
                                com.example.demo.domain.reward.RewardImageRepository rewardImageRepository,
                                com.example.demo.domain.notification.NotificationRepository notificationRepository,
                                DrawResultRepository drawResultRepository) {
        this.premiumRewardRepository = premiumRewardRepository;
        this.drawEntryRepository     = drawEntryRepository;
        this.premiumRedemptionRepository = premiumRedemptionRepository;
        this.userRepository          = userRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.emailService            = emailService;
        this.rewardImageRepository   = rewardImageRepository;
        this.notificationRepository  = notificationRepository;
        this.drawResultRepository    = drawResultRepository;
    }

    // ==============================================
    // CATÁLOGO
    // ==============================================

    public List<PremiumRewardDto> getCatalog(User user, boolean isPremium, PremiumRewardType type) {
        List<PremiumReward> rewards = type != null
                ? premiumRewardRepository.findByActiveTrueAndDeletedFalseAndType(type)
                : premiumRewardRepository.findByActiveTrueAndDeletedFalse();

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

        if (reward.getType() == PremiumRewardType.SORTEO) {
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

        // Disparar mail
        try {
            emailService.sendPremiumRedemptionEmail(
                    user.getEmail(),
                    user.getName(),
                    reward.getName(),
                    code
            );
        } catch (Exception e) {
            log.warn("No se pudo enviar mail de canje premium: {}", e.getMessage());
        }

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

        // Filtrar solo usuarios con premium activo al momento del sorteo
        List<PremiumDrawEntry> elegibles = entries.stream()
                .filter(e -> e.getUser().isActivePremium())
                .collect(java.util.stream.Collectors.toList());

        if (elegibles.isEmpty()) {
            throw new IllegalStateException("No hay participantes con suscripción Premium activa");
        }

        // Mezclar aleatoriamente y tomar hasta 3
        Collections.shuffle(elegibles);
        List<PremiumDrawEntry> seleccionados = elegibles.subList(0, Math.min(3, elegibles.size()));

        // Guardar resultados en draw_results
        for (int i = 0; i < seleccionados.size(); i++) {
            DrawResult result = new DrawResult();
            result.setReward(reward);
            result.setUser(seleccionados.get(i).getUser());
            result.setPosition(i + 1);
            result.setStatus("ACTIVO");
            drawResultRepository.save(result);
        }

        // El ganador es posición 1
        User winner = seleccionados.get(0).getUser();

        // Registrar ganador en el reward (compatibilidad con código existente)
        reward.setWinner(winner);
        reward.setDrawExecuted(true);
        reward.setDrawDate(LocalDateTime.now());
        premiumRewardRepository.save(reward);

        // Notificar al ganador
        try {
            Notification winnerNotif = new Notification();
            winnerNotif.setUser(winner);
            winnerNotif.setActorName("Cinemarketer");
            winnerNotif.setType(com.example.demo.domain.notification.NotificationType.DRAW_WINNER);
            winnerNotif.setMessage("🏆 ¡Felicitaciones! Ganaste el sorteo \"" + reward.getName() + "\". Nuestro equipo se contactará con vos.");
            notificationRepository.save(winnerNotif);

            SupportTicket ticket = new SupportTicket();
            ticket.setUser(winner);
            ticket.setSubject("¡Ganaste el sorteo: " + reward.getName() + "!");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            SupportMessage message = new SupportMessage();
            message.setTicket(savedTicket);
            message.setSenderType(SenderType.ADMIN);
            message.setSenderName("Cinemarketer");
            message.setContent("¡Felicitaciones " + winner.getName() + "!\n\n" +
                    "Sos el ganador del sorteo \"" + reward.getName() + "\".\n\n" +
                    "Necesitamos que nos confirmes la recepción de este mensaje. Nuestro equipo se va a contactar con vos a la brevedad para coordinar " +
                    "la entrega del premio. Podés acercarnos cualquier otra consulta.\n\n" +
                    "Equipo Cinemarketer.");
            message.setReadByAdmin(true);
            message.setReadByUser(false);
            supportMessageRepository.save(message);

            emailService.sendDrawWinnerEmail(
                    winner.getEmail(),
                    winner.getName(),
                    reward.getName()
            );

            log.info("📩 Notificación enviada al ganador: {}", winner.getEmail());
        } catch (Exception e) {
            log.warn("⚠️ No se pudo notificar al ganador: {}", e.getMessage());
        }

        log.info("🏆 Sorteo ejecutado: {} — Ganador: {} — Seleccionados: {}",
                reward.getName(), winner.getEmail(), seleccionados.size());

        Map<String, Object> resultMap = new java.util.LinkedHashMap<>();
        resultMap.put("rewardId", reward.getId());
        resultMap.put("rewardName", reward.getName());
        resultMap.put("totalParticipants", entries.size());
        resultMap.put("elegibles", elegibles.size());
        resultMap.put("winnerId", winner.getId());
        resultMap.put("winnerName", winner.getName());
        resultMap.put("winnerEmail", winner.getEmail());
        if (seleccionados.size() > 1) {
            resultMap.put("suplente1Id", seleccionados.get(1).getUser().getId());
            resultMap.put("suplente1Name", seleccionados.get(1).getUser().getName());
        }
        if (seleccionados.size() > 2) {
            resultMap.put("suplente2Id", seleccionados.get(2).getUser().getId());
            resultMap.put("suplente2Name", seleccionados.get(2).getUser().getName());
        }
        resultMap.put("executedAt", LocalDateTime.now().toString());
        return resultMap;
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

        // Descuento
        dto.setDiscountValue(reward.getDiscountValue());
        dto.setDiscountType(reward.getDiscountType());
        dto.setDiscountCode(reward.getDiscountCode());
        dto.setDiscountChannel(reward.getDiscountChannel());
        dto.setMinimumPurchase(reward.getMinimumPurchase());
        dto.setApplicableProducts(reward.getApplicableProducts());
        dto.setStackable(reward.getStackable());

        // Experiencia
        dto.setExperienceType(reward.getExperienceType());
        dto.setLocation(reward.getLocation());
        dto.setEventDate(reward.getEventDate());
        dto.setMaxCapacity(reward.getMaxCapacity());
        dto.setDuration(reward.getDuration());
        dto.setIncludesTransport(reward.getIncludesTransport());
        dto.setRequirements(reward.getRequirements());
        dto.setCompanionAllowed(reward.getCompanionAllowed());

        // Merchandising
        dto.setBrand(reward.getBrand());
        dto.setMaterial(reward.getMaterial());
        dto.setColor(reward.getColor());
        dto.setSize(reward.getSize());
        dto.setDimensions(reward.getDimensions());
        dto.setWeight(reward.getWeight());
        dto.setOrigin(reward.getOrigin());
        dto.setUnitsIncluded(reward.getUnitsIncluded());
        dto.setCondition(reward.getCondition());

        // Entrada de cine
        dto.setCinemaChain(reward.getCinemaChain());
        dto.setCinemaFormat(reward.getCinemaFormat());
        dto.setCinemaRestrictions(reward.getCinemaRestrictions());
        dto.setTicketsIncluded(reward.getTicketsIncluded());
        dto.setIncludesSnack(reward.getIncludesSnack());

        if (reward.getWinner() != null) {
            dto.setWinnerName(reward.getWinner().getName());
        }

        // Resultados del sorteo (ganador + suplentes)
        if (reward.getType() == PremiumRewardType.SORTEO && reward.isDrawExecuted()) {
            List<DrawResult> results = drawResultRepository.findByRewardIdOrderByPosition(reward.getId());
            for (DrawResult dr : results) {
                if (dr.getPosition() == 1) {
                    dto.setWinner1Name(dr.getUser().getName());
                    dto.setWinner1Id(dr.getUser().getId());
                } else if (dr.getPosition() == 2) {
                    dto.setWinner2Name(dr.getUser().getName());
                    dto.setWinner2Id(dr.getUser().getId());
                } else if (dr.getPosition() == 3) {
                    dto.setWinner3Name(dr.getUser().getName());
                    dto.setWinner3Id(dr.getUser().getId());
                }
            }
        }

        if (reward.getType() != PremiumRewardType.SORTEO) {
            dto.setCanRedeem(user.isActivePremium()
                    && reward.hasStock()
                    && user.getTotalPoints() >= reward.getPointsRequired()
                    && !premiumRedemptionRepository.existsByRewardIdAndUserId(reward.getId(), user.getId()));
        } else if (reward.getType() == PremiumRewardType.SORTEO) {
            dto.setAlreadyEntered(drawEntryRepository.existsByRewardIdAndUserId(reward.getId(), user.getId()));
            dto.setTotalEntries(drawEntryRepository.countByRewardId(reward.getId()));
        }

        // Cargar imágenes
        var images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(reward.getId(), "PREMIUM");
        dto.setImages(images.stream().map(img -> {
            PremiumRewardDto.ImageDto imgDto = new PremiumRewardDto.ImageDto();
            imgDto.setId(img.getId());
            imgDto.setImageUrl(img.getImageUrl());
            imgDto.setPrimary(img.isPrimary());
            return imgDto;
        }).collect(java.util.stream.Collectors.toList()));

        return dto;
    }
}