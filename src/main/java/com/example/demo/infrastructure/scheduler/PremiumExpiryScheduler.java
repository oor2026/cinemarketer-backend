package com.example.demo.infrastructure.scheduler;

import com.example.demo.domain.notification.Notification;
import com.example.demo.domain.notification.NotificationRepository;
import com.example.demo.domain.notification.NotificationType;
import com.example.demo.domain.subscription.SubscriptionStatus;
import com.example.demo.domain.subscription.UserSubscription;
import com.example.demo.domain.subscription.UserSubscriptionRepository;
import com.example.demo.domain.user.UserRepository;
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
    private final UserRepository userRepository;

    public PremiumExpiryScheduler(UserSubscriptionRepository userSubscriptionRepository,
                                  NotificationRepository notificationRepository,
                                  UserRepository userRepository) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // Corre todos los días a las 3:00 AM — antes que checkPremiumExpiry, así
    // las notificaciones de más abajo ya trabajan sobre el estado del día
    // actualizado, no sobre flags vencidos de ayer. is_premium/is_creator no
    // se apagan solos con el tiempo (isActivePremium()/isActiveCreator() sí
    // chequean la fecha para el gating real) — esto es solo para que la
    // tabla no muestre usuarios vencidos como activos en paneles/reportes
    // que filtren por el booleano crudo.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deactivateExpiredFlags() {
        LocalDateTime now = LocalDateTime.now();
        int premiumDesactivados = userRepository.expirePremiumFlags(now);
        int creatorDesactivados = userRepository.expireCreatorFlags(now);
        log.info("🧹 Flags vencidos desactivados — Premium: {}, Creator: {}", premiumDesactivados, creatorDesactivados);
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