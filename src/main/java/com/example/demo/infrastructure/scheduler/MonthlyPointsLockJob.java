package com.example.demo.infrastructure.scheduler;

import com.example.demo.domain.comment.CommentReactionRepository;
import com.example.demo.domain.comment.ReactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job mensual que bloquea los ¡Merecés un punto! activos el 1° de cada mes.
 * Una vez bloqueados, no pueden ser retirados por el usuario que los otorgó.
 * Corre a las 00:05 del día 1 de cada mes.
 */
@Component
public class MonthlyPointsLockJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPointsLockJob.class);

    private final CommentReactionRepository commentReactionRepository;

    public MonthlyPointsLockJob(CommentReactionRepository commentReactionRepository) {
        this.commentReactionRepository = commentReactionRepository;
    }

    @Scheduled(cron = "0 5 0 1 * *") // 00:05 del dia 1 de cada mes
    @Transactional
    public void lockMonthlyPoints() {
        log.info("Iniciando bloqueo mensual de puntos ¡Merecés un punto!...");

        int locked = commentReactionRepository.lockActiveMerecePuntoReactions();

        log.info("Bloqueo mensual completado. {} reacciones bloqueadas.", locked);
    }
}
