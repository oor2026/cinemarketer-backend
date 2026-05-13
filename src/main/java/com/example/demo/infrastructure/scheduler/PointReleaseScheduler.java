package com.example.demo.infrastructure.scheduler;

import com.example.demo.domain.pointbatch.PointBatch;
import com.example.demo.domain.pointbatch.PointBatchRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Job mensual de liberación de puntos acumulados.
 *
 * Se ejecuta el 1° de cada mes a las 00:00 hora Argentina (GMT-3).
 * Cron en UTC: 03:00 del día 1 = "0 0 3 1 * *"
 *
 * Para cada usuario activo:
 *   1. Toma accumulated_points del mes en curso
 *   2. Aplica tope FREE (20.000 pts) — PREMIUM sin tope
 *   3. Crea un lote en point_batches con expires_at = +6 meses (solo FREE)
 *   4. Suma los puntos liberados a available_points
 *   5. Resta los puntos liberados de accumulated_points
 *      (el excedente queda en accumulated_points para el próximo ciclo)
 */
@Component
public class PointReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(PointReleaseScheduler.class);

    private final UserRepository userRepository;
    private final PointBatchRepository pointBatchRepository;

    public PointReleaseScheduler(UserRepository userRepository,
                                 PointBatchRepository pointBatchRepository) {
        this.userRepository = userRepository;
        this.pointBatchRepository = pointBatchRepository;
    }

    /**
     * Liberación mensual de puntos acumulados.
     * Cron UTC: 1° de cada mes a las 03:00 AM = 00:00 Argentina (GMT-3)
     */
    @Scheduled(cron = "0 0 3 1 * *", zone = "UTC")
    @Transactional
    public void releaseMonthlyPoints() {
        LocalDateTime now = LocalDateTime.now();
        log.info("🪙 Iniciando liberación mensual de puntos - {}", now);

        List<User> activeUsers = userRepository.findByActiveTrue();
        int usersProcessed = 0;
        int totalPointsReleased = 0;

        for (User user : activeUsers) {
            try {
                int accumulated = user.getAccumulatedPoints();
                if (accumulated <= 0) continue;

                // Determinar cuánto liberar según FREE/PREMIUM
                Integer cap = user.getEffectiveMonthlyCap();
                int toRelease = (cap == null) ? accumulated : Math.min(accumulated, cap);

                // Fecha de vencimiento: 6 meses solo para FREE — PREMIUM no vence
                LocalDateTime expiresAt = user.isPremium() ? null : now.plusMonths(6);

                // Crear lote de puntos
                PointBatch batch = new PointBatch();
                batch.setUser(user);
                batch.setPoints(toRelease);
                batch.setRemainingPoints(toRelease);
                batch.setReleasedAt(now);
                batch.setExpiresAt(expiresAt);
                batch.setExpired(false);
                pointBatchRepository.save(batch);

                // Actualizar el usuario
                user.addAvailablePoints(toRelease);
                user.clearAccumulatedPoints(toRelease);
                userRepository.save(user);

                usersProcessed++;
                totalPointsReleased += toRelease;

                log.debug("  - Usuario {}: {} pts liberados (acumulados: {}, excedente: {})",
                        user.getEmail(), toRelease, accumulated,
                        Math.max(0, accumulated - toRelease));

            } catch (Exception e) {
                log.error("❌ Error procesando usuario {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("✅ Liberación completada: {} usuarios procesados, {} puntos liberados",
                usersProcessed, totalPointsReleased);
    }

    /**
     * Vencimiento de puntos FREE.
     * Corre diariamente a las 03:05 AM UTC para marcar lotes expirados.
     */
    @Scheduled(cron = "0 5 3 * * *", zone = "UTC")
    @Transactional
    public void expirePoints() {
        LocalDateTime now = LocalDateTime.now();
        log.info("⏰ Verificando vencimiento de puntos - {}", now);

        List<PointBatch> expiredBatches = pointBatchRepository.findExpiredBatches(now);
        int totalExpired = 0;

        for (PointBatch batch : expiredBatches) {
            try {
                User user = batch.getUser();
                int pointsToExpire = batch.getRemainingPoints();

                // Descontar los puntos vencidos de available_points
                if (user.getAvailablePoints() >= pointsToExpire) {
                    user.setAvailablePoints(user.getAvailablePoints() - pointsToExpire);
                } else {
                    user.setAvailablePoints(0);
                }

                batch.setExpired(true);
                batch.setRemainingPoints(0);

                pointBatchRepository.save(batch);
                userRepository.save(user);

                totalExpired += pointsToExpire;

                log.debug("  - Usuario {}: {} pts vencidos (lote del {})",
                        user.getEmail(), pointsToExpire, batch.getReleasedAt());

            } catch (Exception e) {
                log.error("❌ Error venciendo lote {}: {}", batch.getId(), e.getMessage());
            }
        }

        if (totalExpired > 0) {
            log.info("✅ Vencimiento completado: {} pts expirados en {} lotes",
                    totalExpired, expiredBatches.size());
        }
    }
}