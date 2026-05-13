package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.services.LevelCalculatorService;
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

/**
 * Scheduler para actualización de niveles de usuario.
 * El nivel es una insignia acumulativa basada en totalRedeemedPoints — nunca baja.
 */
@Component
public class LevelUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(LevelUpdateScheduler.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final LevelCalculatorService levelCalculatorService;

    public LevelUpdateScheduler(UserRepository userRepository,
                                UserService userService,
                                LevelCalculatorService levelCalculatorService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.levelCalculatorService = levelCalculatorService;
    }

    /**
     * Actualización diaria de niveles — todos los días a las 03:10 AM UTC (00:10 Argentina)
     * Solo sube de nivel, nunca baja.
     */
    @Scheduled(cron = "0 10 3 * * *", zone = "UTC")
    @Transactional
    public void updateAllLevelsDaily() {
        log.info("🔄 Iniciando actualización diaria de niveles - {}", LocalDateTime.now());

        try {
            List<User> allUsers = userRepository.findAll();
            int promoted = 0;

            for (User user : allUsers) {
                UserLevel oldLevel = user.getLevel();
                UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);

                // Solo subir — nunca bajar
                if (newLevel.ordinal() > oldLevel.ordinal()) {
                    user.setLevel(newLevel);
                    user.setLevelUpdatedAt(LocalDateTime.now());
                    userService.resetToDefaultAvatar(user.getId());
                    userRepository.save(user);
                    promoted++;
                    log.debug("  - Usuario {}: {} → {}", user.getEmail(), oldLevel, newLevel);
                }
            }

            log.info("✅ Actualización completada: {} usuarios promovidos", promoted);

        } catch (Exception e) {
            log.error("❌ Error en actualización diaria de niveles", e);
        }
    }

    /**
     * Verificación cada 6 horas de usuarios elegibles para subir de nivel.
     */
    @Scheduled(cron = "0 0 */6 * * *", zone = "UTC")
    @Transactional
    public void updateEligibleUsers() {
        log.info("🔄 Verificando usuarios elegibles para subir de nivel - {}", LocalDateTime.now());

        try {
            List<User> eligibleUsers = userRepository.findUsersEligibleForLevelUp();
            int promoted = 0;

            for (User user : eligibleUsers) {
                UserLevel oldLevel = user.getLevel();
                UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);

                if (newLevel.ordinal() > oldLevel.ordinal()) {
                    user.setLevel(newLevel);
                    user.setLevelUpdatedAt(LocalDateTime.now());
                    userService.resetToDefaultAvatar(user.getId());
                    userRepository.save(user);
                    promoted++;
                    log.debug("  - Usuario {} promovido a {}", user.getEmail(), newLevel);
                }
            }

            log.info("✅ Verificación completada: {} usuarios promovidos", promoted);

        } catch (Exception e) {
            log.error("❌ Error en verificación de usuarios elegibles", e);
        }
    }

    /**
     * Mantenimiento semanal — domingos a las 04:00 AM UTC.
     * Corrige inconsistencias: si un usuario tiene totalRedeemedPoints
     * suficientes para un nivel superior pero no fue actualizado.
     */
    @Scheduled(cron = "0 0 4 * * 0", zone = "UTC")
    @Transactional
    public void maintenanceTask() {
        log.info("🛠️ Ejecutando mantenimiento de niveles - {}", LocalDateTime.now());

        try {
            // Buscar usuarios con nivel inconsistente
            // (tienen totalRedeemedPoints para Jurado Experto pero nivel menor)
            List<User> inconsistentUsers = userRepository
                    .findByLevelAndTotalRedeemedPointsLessThan(UserLevel.JURADO_EXPERTO, 60000);

            int corrected = 0;
            for (User user : inconsistentUsers) {
                UserLevel correctLevel = levelCalculatorService.calculateUserLevel(user);
                // Solo corregir si el nivel calculado es mayor (no bajar)
                if (correctLevel.ordinal() > user.getLevel().ordinal()) {
                    user.setLevel(correctLevel);
                    user.setLevelUpdatedAt(LocalDateTime.now());
                    userRepository.save(user);
                    corrected++;
                }
            }

            log.info("✅ Mantenimiento completado: {} usuarios corregidos", corrected);

        } catch (Exception e) {
            log.error("❌ Error en tarea de mantenimiento", e);
        }
    }
}