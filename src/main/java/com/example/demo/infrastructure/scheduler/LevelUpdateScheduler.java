package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.services.LevelCalculatorService;
import com.example.demo.application.services.NotificationService;
import com.example.demo.application.services.UserService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class LevelUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(LevelUpdateScheduler.class);

    // Puntos de premio por ascenso
    private static final int PREMIO_COLABORADOR    = 500;
    private static final int PREMIO_CRITICO        = 1000;
    private static final int PREMIO_JURADO_EXPERTO = 3000;

    private final UserRepository userRepository;
    private final UserService userService;
    private final LevelCalculatorService levelCalculatorService;
    private final NotificationService notificationService;

    public LevelUpdateScheduler(UserRepository userRepository,
                                UserService userService,
                                LevelCalculatorService levelCalculatorService,
                                NotificationService notificationService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.levelCalculatorService = levelCalculatorService;
        this.notificationService = notificationService;
    }

    /**
     * Actualización nocturna — todos los días a las 03:00 AM UTC (00:00 Argentina)
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void updateAllLevelsDaily() {
        log.info("🔄 Iniciando actualización nocturna de insignias - {}", LocalDateTime.now());

        try {
            List<User> allUsers = userRepository.findByActiveTrue();
            int promoted = 0;

            for (User user : allUsers) {
                UserLevel oldLevel = user.getLevel();
                UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);

                // Solo subir — nunca bajar
                if (newLevel.ordinal() > oldLevel.ordinal()) {
                    user.setLevel(newLevel);
                    user.setLevelUpdatedAt(LocalDateTime.now());

                    // Premio en puntos disponibles
                    int premio = getPremio(newLevel);
                    if (premio > 0) {
                        user.addAvailablePoints(premio);
                        user.setTotalRedeemedPoints(user.getTotalRedeemedPoints()); // no afecta canjeados
                    }

                    userRepository.save(user);

                    // Resetear avatar al del nuevo nivel
                    try { userService.resetToDefaultAvatar(user.getId()); } catch (Exception ignored) {}

                    // Notificación 1: ascenso de insignia
                    try {
                        notificationService.crearAscensoInsignia(user, oldLevel, newLevel);
                    } catch (Exception e) {
                        log.warn("No se pudo notificar ascenso a {}: {}", user.getEmail(), e.getMessage());
                    }

                    // Notificación 2: puntos de regalo
                    if (premio > 0) {
                        try {
                            notificationService.crearPremioInsignia(user, newLevel, premio);
                        } catch (Exception e) {
                            log.warn("No se pudo notificar premio a {}: {}", user.getEmail(), e.getMessage());
                        }
                    }

                    promoted++;
                    log.info("  ⬆ {} promovido: {} → {} (+{} pts)",
                            user.getEmail(), oldLevel, newLevel, premio);
                }
            }

            log.info("✅ Actualización completada: {} usuarios promovidos", promoted);

        } catch (Exception e) {
            log.error("❌ Error en actualización nocturna de insignias", e);
        }
    }

    private int getPremio(UserLevel nivel) {
        return switch (nivel) {
            case COLABORADOR    -> PREMIO_COLABORADOR;
            case CRITICO        -> PREMIO_CRITICO;
            case JURADO_EXPERTO -> PREMIO_JURADO_EXPERTO;
            default             -> 0;
        };
    }
}