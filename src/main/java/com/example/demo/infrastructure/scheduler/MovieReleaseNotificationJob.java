package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.services.MovieService;
import com.example.demo.application.services.NotificationService;
import com.example.demo.domain.expectation.MovieExpectation;
import com.example.demo.domain.expectation.MovieExpectationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Job diario que revisa las películas "Lo que se viene" con aviso de
 * estreno activado (calificadas 4-5 estrellas de expectativa + el
 * usuario confirmó que quiere el aviso). Si ya se estrenaron, dispara
 * la notificación y marca notified=true para no volver a avisar.
 * Corre a las 09:00 todos los días.
 */
@Component
public class MovieReleaseNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(MovieReleaseNotificationJob.class);

    private final MovieExpectationRepository movieExpectationRepository;
    private final MovieService movieService;
    private final NotificationService notificationService;

    public MovieReleaseNotificationJob(MovieExpectationRepository movieExpectationRepository,
                                       MovieService movieService,
                                       NotificationService notificationService) {
        this.movieExpectationRepository = movieExpectationRepository;
        this.movieService = movieService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 9 * * *") // 09:00 todos los días
    @Transactional
    public void notificarEstrenos() {
        log.info("Revisando estrenos esperados con aviso pendiente...");

        List<MovieExpectation> pendientes = movieExpectationRepository
                .findByNotifyOnReleaseTrueAndNotifiedFalse();

        int notificadas = 0;
        for (MovieExpectation exp : pendientes) {
            try {
                var movie = movieService.getMovieDetails(exp.getMovieId());
                if (movie == null || movie.getReleaseDate() == null || movie.getReleaseDate().isBlank()) {
                    continue;
                }
                LocalDate estreno = LocalDate.parse(movie.getReleaseDate());
                if (!estreno.isAfter(LocalDate.now())) {
                    notificationService.crearEstrenoEsperado(exp.getUser(), exp.getMovieId(), movie.getTitle());
                    exp.setNotified(true);
                    movieExpectationRepository.save(exp);
                    notificadas++;
                }
            } catch (Exception e) {
                log.error("Error revisando estreno de película {}: {}", exp.getMovieId(), e.getMessage());
            }
        }

        log.info("Notificaciones de estreno enviadas: {}", notificadas);
    }
}