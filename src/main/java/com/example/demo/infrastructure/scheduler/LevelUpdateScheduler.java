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
 * Tarea programada para actualizar los niveles de los usuarios
 * Se ejecuta periódicamente en segundo plano
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

    // ==============================================
    // TAREAS PROGRAMADAS
    // ==============================================

    /**
     * Actualización diaria de niveles
     * Se ejecuta todos los días a las 3:00 AM
     *
     * CRON: segundo minuto hora día mes día-semana
     * 0 0 3 * * * = todos los días a las 3:00:00
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void updateAllLevelsDaily() {
        log.info("🔄 Iniciando actualización diaria de niveles - {}", LocalDateTime.now());

        try {
            List<User> allUsers = userRepository.findAll();
            int updated = 0;
            int promoted = 0;
            int demoted = 0;  // Aunque no bajamos de nivel, lo medimos igual

            for (User user : allUsers) {
                UserLevel oldLevel = user.getLevel();
                UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);

                if (oldLevel != newLevel) {
                    user.setLevel(newLevel);
                    user.setLevelUpdatedAt(LocalDateTime.now());

                    // Si subió de nivel, asignar avatar por defecto del nuevo nivel
                    if (newLevel.ordinal() > oldLevel.ordinal()) {
                        userService.resetToDefaultAvatar(user.getId());
                        promoted++;
                    } else {
                        demoted++;
                    }

                    userRepository.save(user);
                    updated++;

                    log.debug("  - Usuario {}: {} → {}",
                            user.getEmail(), oldLevel, newLevel);
                }
            }

            log.info("✅ Actualización completada: {} usuarios actualizados ({} promovidos, {} degradados)",
                    updated, promoted, demoted);

        } catch (Exception e) {
            log.error("❌ Error en actualización diaria de niveles", e);
        }
    }

    /**
     * Actualización de usuarios elegibles para subir de nivel
     * Se ejecuta cada 6 horas (más frecuente que la diaria)
     *
     * CRON: 0 0 6 * * * = cada 6 horas */

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void updateEligibleUsers() {
        log.info("🔄 Verificando usuarios elegibles para subir de nivel - {}", LocalDateTime.now());

        try {
            List<User> eligibleUsers = userRepository.findUsersEligibleForLevelUp();
            int promoted = 0;

            for (User user : eligibleUsers) {
                UserLevel oldLevel = user.getLevel();
                UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);

                if (oldLevel != newLevel && newLevel.ordinal() > oldLevel.ordinal()) {
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
     * Tarea de mantenimiento: limpia usuarios inactivos o con datos inconsistentes
     * Se ejecuta los domingos a las 4:00 AM
     *
     * CRON: 0 0 4 * * 0 = todos los domingos a las 4 AM
     */
    @Scheduled(cron = "0 0 4 * * 0")
    @Transactional
    public void maintenanceTask() {
        log.info("🛠️ Ejecutando tarea de mantenimiento de niveles - {}", LocalDateTime.now());

        try {
            // Buscar usuarios con nivel inconsistente (ej: nivel alto pero puntos bajos)
            List<User> inconsistentUsers = findUsersWithInconsistentLevels();

            for (User user : inconsistentUsers) {
                UserLevel correctLevel = levelCalculatorService.calculateUserLevel(user);
                if (user.getLevel() != correctLevel) {
                    user.setLevel(correctLevel);
                    user.setLevelUpdatedAt(LocalDateTime.now());
                    userRepository.save(user);
                    log.debug("  - Corregido nivel de usuario {}: {} → {}",
                            user.getEmail(), user.getLevel(), correctLevel);
                }
            }

            log.info("✅ Mantenimiento completado: {} usuarios corregidos", inconsistentUsers.size());

        } catch (Exception e) {
            log.error("❌ Error en tarea de mantenimiento", e);
        }
    }

    // ==============================================
    // MÉTODOS AUXILIARES
    // ==============================================

    /**
     * Busca usuarios con niveles potencialmente inconsistentes
     */
    private List<User> findUsersWithInconsistentLevels() {
        // Por ejemplo: Jurado Experto con menos de 1000 puntos
        return userRepository.findByLevelAndTotalPointsLessThan(
                UserLevel.JURADO_EXPERTO, 1000);
    }

    /**
     * Tarea de prueba (ejecutar cada minuto) - Solo para desarrollo
     * Comentar o eliminar en producción
     */
    // @Scheduled(fixedDelay = 60000)  // Cada 60 segundos
    public void testTask() {
        log.debug("🧪 Tarea de prueba ejecutándose...");
        // Lógica de prueba
    }
}