package com.example.demo.infrastructure.scheduler;

import com.example.demo.domain.notification.Notification;
import com.example.demo.domain.notification.NotificationRepository;
import com.example.demo.domain.notification.NotificationType;
import com.example.demo.domain.subscription.SubscriptionStatus;
import com.example.demo.domain.subscription.UserSubscription;
import com.example.demo.domain.subscription.UserSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PremiumExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PremiumExpiryScheduler.class);

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final NotificationRepository notificationRepository;

    public PremiumExpiryScheduler(UserSubscriptionRepository userSubscriptionRepository,
                                  NotificationRepository notificationRepository) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.notificationRepository = notificationRepository;
    }

    // Corre todos los días a las 9:00 AM
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void checkPremiumExpiry() {
        log.info("🔔 Verificando vencimientos de suscripciones premium...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in7Days = now.plusDays(7);
        LocalDateTime in1Day  = now.plusDays(1);

        // Suscripciones canceladas con período de gracia que vencen en 7 días (+/- 12hs)
        List<UserSubscription> expiring7 = userSubscriptionRepository
                .findByStatusAndEndDateBetween(
                        SubscriptionStatus.CANCELLED,
                        in7Days.minusHours(12),
                        in7Days.plusHours(12)
                );

        for (UserSubscription sub : expiring7) {
            boolean yaNotificado = notificationRepository
                    .existsByUserIdAndTypeAndCreatedAtAfter(
                            sub.getUser().getId(),
                            NotificationType.PREMIUM_EXPIRING_SOON,
                            now.minusDays(8));
            if (!yaNotificado) {
                Notification notif = new Notification();
                notif.setUser(sub.getUser());
                notif.setActorName("Cinemarketer");
                notif.setType(NotificationType.PREMIUM_EXPIRING_SOON);
                notif.setMessage("Tu suscripción Premium vence en 7 días. ¡Renovála y mantené todos tus beneficios!");
                notificationRepository.save(notif);
                log.info("📩 Notif 7 días enviada a: {}", sub.getUser().getEmail());
            }
        }

        // Suscripciones canceladas que vencen mañana (+/- 12hs)
        List<UserSubscription> expiring1 = userSubscriptionRepository
                .findByStatusAndEndDateBetween(
                        SubscriptionStatus.CANCELLED,
                        in1Day.minusHours(12),
                        in1Day.plusHours(12)
                );

        for (UserSubscription sub : expiring1) {
            boolean yaNotificado = notificationRepository
                    .existsByUserIdAndTypeAndCreatedAtAfter(
                            sub.getUser().getId(),
                            NotificationType.PREMIUM_EXPIRING_TOMORROW,
                            now.minusDays(2));
            if (!yaNotificado) {
                Notification notif = new Notification();
                notif.setUser(sub.getUser());
                notif.setActorName("Cinemarketer");
                notif.setType(NotificationType.PREMIUM_EXPIRING_TOMORROW);
                notif.setMessage("Tu suscripción Premium vence mañana. ¡No pierdas tus beneficios Premium!");
                notificationRepository.save(notif);
                log.info("📩 Notif 1 día enviada a: {}", sub.getUser().getEmail());
            }
        }

        log.info("✅ Verificación de vencimientos completada.");
    }
}