package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.services.AdminNotificationService;
import com.example.demo.domain.notification.AdminNotificationCampaign;
import com.example.demo.domain.notification.AdminNotificationCampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AdminNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationScheduler.class);

    private final AdminNotificationCampaignRepository campaignRepository;
    private final AdminNotificationService adminNotificationService;

    public AdminNotificationScheduler(AdminNotificationCampaignRepository campaignRepository,
                                      AdminNotificationService adminNotificationService) {
        this.campaignRepository = campaignRepository;
        this.adminNotificationService = adminNotificationService;
    }

    // Corre cada minuto — busca campañas programadas cuya hora ya llegó
    @Scheduled(cron = "0 * * * * *")
    public void enviarProgramadas() {
        List<AdminNotificationCampaign> pendientes = campaignRepository
                .findByStatusAndScheduledAtLessThanEqual("PENDING", LocalDateTime.now());

        for (AdminNotificationCampaign campaign : pendientes) {
            try {
                adminNotificationService.enviar(campaign);
            } catch (Exception e) {
                log.error("❌ Error enviando campaña programada {}: {}", campaign.getId(), e.getMessage());
            }
        }
    }
}