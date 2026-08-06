package com.example.demo.application.services;

import com.example.demo.domain.notification.AdminNotificationCampaign;
import com.example.demo.domain.notification.AdminNotificationCampaignRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationService.class);

    private final AdminNotificationCampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AdminNotificationService(AdminNotificationCampaignRepository campaignRepository,
                                    UserRepository userRepository,
                                    NotificationService notificationService) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public AdminNotificationCampaign crear(String title, String message, String targetType,
                                           List<String> segments, List<Long> userIds,
                                           LocalDateTime scheduledAt, String createdBy) {
        AdminNotificationCampaign campaign = new AdminNotificationCampaign();
        campaign.setTitle(title);
        campaign.setMessage(message);
        campaign.setTargetType(targetType);
        campaign.setTargetSegments(segments != null ? segments.toArray(new String[0]) : null);
        campaign.setTargetUserIds(userIds != null
                ? userIds.stream().map(String::valueOf).toArray(String[]::new) : null);
        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus("PENDING");
        campaign.setCreatedBy(createdBy);
        campaignRepository.save(campaign);

        boolean esInmediato = scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now());
        if (esInmediato) {
            enviar(campaign);
        }
        return campaign;
    }

    @Transactional
    public void enviar(AdminNotificationCampaign campaign) {
        List<User> destinatarios = resolverDestinatarios(campaign);

        for (User u : destinatarios) {
            try {
                notificationService.crearNotificacionAdmin(u, campaign.getTitle(), campaign.getMessage());
            } catch (Exception e) {
                log.warn("❌ Error notificando a usuario {}: {}", u.getId(), e.getMessage());
            }
        }

        campaign.setStatus("SENT");
        campaign.setSentAt(LocalDateTime.now());
        campaign.setRecipientsCount(destinatarios.size());
        campaignRepository.save(campaign);

        log.info("📢 Campaña '{}' enviada a {} usuarios", campaign.getTitle(), destinatarios.size());
    }

    private List<User> resolverDestinatarios(AdminNotificationCampaign campaign) {
        if ("USERS".equals(campaign.getTargetType())) {
            if (campaign.getTargetUserIds() == null) return List.of();
            List<Long> ids = Arrays.stream(campaign.getTargetUserIds()).map(Long::parseLong).toList();
            return userRepository.findAllById(ids);
        }

        // SEGMENTS: FREE / PREMIUM / CREATOR — combinables (OR entre los elegidos)
        Set<String> segmentos = campaign.getTargetSegments() != null
                ? new HashSet<>(Arrays.asList(campaign.getTargetSegments())) : Set.of();

        List<User> resultado = new ArrayList<>();
        for (User u : userRepository.findByActiveTrue()) {
            boolean esPremium = u.isActivePremium();
            boolean esCreator = u.isActiveCreator();
            boolean esFree = !esPremium && !esCreator;

            if ((segmentos.contains("FREE") && esFree)
                    || (segmentos.contains("PREMIUM") && esPremium)
                    || (segmentos.contains("CREATOR") && esCreator)) {
                resultado.add(u);
            }
        }
        return resultado;
    }

    public List<AdminNotificationCampaign> listar() {
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }
}