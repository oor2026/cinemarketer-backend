package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.application.services.EmailService;
import com.example.demo.application.services.MovieService;
import com.example.demo.application.services.NotificationService;
import com.example.demo.domain.expectation.MovieExpectation;
import com.example.demo.domain.expectation.MovieExpectationRepository;
import com.example.demo.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job diario que revisa las películas "Lo que se viene" con aviso de estreno activado (la persona dijo que
 * la espera y confirmó que quiere el aviso). Si ya se estrenaron, avisa por la campanita y por mail, y marca
 * notified=true para no volver a avisar. Si un día no corre (reinicio, deploy) o un aviso falla, al día siguiente
 * se pone al día: avisa todo lo que ya se estrenó y no se avisó.
 * Corre a las 09:00 de Argentina todos los días.
 *
 * Cada aviso va en SU PROPIA transacción: si uno falla (por ejemplo, la base rechaza guardar la notificación) se
 * deshace solo ese, y los demás se avisan igual. Antes todo el job era una única transacción y un solo error
 * dejaba sin avisar a todos.
 */
@Component
public class MovieReleaseNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(MovieReleaseNotificationJob.class);
    private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342";

    private final MovieExpectationRepository movieExpectationRepository;
    private final MovieService movieService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    public MovieReleaseNotificationJob(MovieExpectationRepository movieExpectationRepository,
                                       MovieService movieService,
                                       NotificationService notificationService,
                                       EmailService emailService,
                                       TransactionTemplate transactionTemplate) {
        this.movieExpectationRepository = movieExpectationRepository;
        this.movieService = movieService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.transactionTemplate = transactionTemplate;
    }

    /** A quién hay que mandarle el mail una vez que su aviso quedó confirmado. */
    private record MailPendiente(String email, String nombre) {}

    /** Qué pasó con un pedido: avisado (se creó la notificación) o descartado (ya avisado, o cambió a "No"). */
    private record Resultado(boolean avisado, MailPendiente mail) {}

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Argentina/Buenos_Aires") // 09:00 de Argentina, todos los días
    public void notificarEstrenos() {
        log.info("Revisando estrenos esperados con aviso pendiente...");

        LocalDate hoy = LocalDate.now(ZONA_ARGENTINA);
        List<MovieExpectation> pendientes = movieExpectationRepository
                .findByNotifyOnReleaseTrueAndNotifiedFalse();
        log.info("Avisos pendientes: {}", pendientes.size());

        // Muchas personas pueden esperar la misma película: se le pregunta una sola vez a TMDb.
        Map<Long, TmdbMovieDto> peliculas = new HashMap<>();
        int notificadas = 0, sinEstrenar = 0, descartadas = 0, conError = 0;

        for (MovieExpectation pendiente : pendientes) {
            Long expectationId = pendiente.getId();
            Long movieId = pendiente.getMovieId();
            try {
                // La consulta a TMDb va FUERA de la transacción: no se tiene la base tomada mientras se espera a internet.
                TmdbMovieDto movie = peliculas.containsKey(movieId) ? peliculas.get(movieId) : movieService.getMovieDetails(movieId);
                peliculas.put(movieId, movie);
                // getReleaseDateAsLocalDate() devuelve null si la fecha falta o no se puede leer.
                LocalDate estreno = movie == null ? null : movie.getReleaseDateAsLocalDate();
                if (estreno == null) {
                    continue;
                }
                if (estreno.isAfter(hoy)) {
                    sinEstrenar++;
                    continue;
                }

                final String titulo = movie.getTitle();
                final String poster = urlPoster(movie.getPosterPath());
                Resultado resultado = transactionTemplate.execute(status -> avisar(expectationId, movieId, titulo));
                if (resultado != null && resultado.avisado()) {
                    notificadas++;
                    if (resultado.mail() != null) enviarMail(resultado.mail(), titulo, poster);
                } else {
                    descartadas++;
                }
            } catch (Exception e) {
                conError++;
                log.error("Error avisando el estreno de la película {} (expectativa {}): {}", movieId, expectationId, e.getMessage(), e);
            }
        }

        log.info("Estrenos: {} avisados, {} todavía sin estrenar, {} descartados (ya avisados o cambiaron a No), {} con error.",
                notificadas, sinEstrenar, descartadas, conError);
    }

    /**
     * Dentro de la transacción de UN aviso: crea la notificación y marca notified=true. Informa si se avisó y a quién
     * mandarle el mail (null si no corresponde: no tiene el mail verificado, la cuenta no está activa, etc.).
     */
    private Resultado avisar(Long expectationId, Long movieId, String titulo) {
        MovieExpectation exp = movieExpectationRepository.findById(expectationId).orElse(null);
        if (exp == null || exp.isNotified() || !exp.isNotifyOnRelease()) return new Resultado(false, null);   // ya lo resolvió otro

        // Dijo "Sí", pidió el aviso, y después cambió a "No": ya no hay que avisarle.
        if (!exp.isExpecting()) {
            exp.setNotifyOnRelease(false);
            movieExpectationRepository.save(exp);
            return new Resultado(false, null);
        }

        User user = exp.getUser();
        notificationService.crearEstrenoEsperado(user, movieId, titulo);
        exp.setNotified(true);
        movieExpectationRepository.save(exp);

        boolean puedeRecibirMail = user != null && user.isEmailVerified() && user.isActive() && !user.isSuspended()
                && user.getEmail() != null && !user.getEmail().isBlank();
        return new Resultado(true, puedeRecibirMail ? new MailPendiente(user.getEmail(), primerNombre(user.getName())) : null);
    }

    /** "Laura Flor Carranza Fernández" → "Laura": el mail saluda por el primer nombre, como el resto de la app. */
    private static String primerNombre(String nombre) {
        if (nombre == null) return null;
        String limpio = nombre.trim();
        int espacio = limpio.indexOf(' ');
        return espacio > 0 ? limpio.substring(0, espacio) : limpio;
    }

    /**
     * Se envía DESPUÉS de que la transacción del aviso se confirmó: nunca sale un mail por un aviso que se deshizo.
     * Si el mail falla, se registra y no afecta a la campanita ni a los demás avisos.
     */
    private void enviarMail(MailPendiente mail, String titulo, String poster) {
        try {
            emailService.sendMovieReleasedEmail(mail.email(), mail.nombre(), titulo, poster, null, null);
        } catch (Exception e) {
            log.error("No se pudo enviar el mail de estreno a {}: {}", mail.email(), e.getMessage());
        }
    }

    /** Dirección del póster en TMDb a partir del poster_path ("/abc123.jpg"); null si la película no tiene póster. */
    private String urlPoster(String posterPath) {
        if (posterPath == null || !posterPath.matches("/[A-Za-z0-9._-]+")) return null;
        return TMDB_POSTER_BASE + posterPath;
    }
}