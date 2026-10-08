package com.example.demo.application.services;

import com.example.demo.domain.cinema.CinemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mantiene la tabla Cinema al día con el directorio de agendadecine.com/cines.
 *
 * - Al arrancar: si la base todavía no tiene el directorio (por ejemplo, producción recién migrada),
 *   lo carga en segundo plano, sin demorar el arranque. Si falla, reintenta unas veces.
 * - Todas las noches, a las 4:00 de Argentina: lo vuelve a sincronizar, así los cines nuevos y los
 *   cambios de dirección o teléfono aparecen solos.
 *
 * No expone ninguna ruta. Reemplaza al endpoint de prueba /api/test/directorio/sincronizar, que se
 * borró junto con AgendaDeCineTestController.
 */
@Component
public class DirectorioSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(DirectorioSyncScheduler.class);
    private static final int INTENTOS_AL_ARRANCAR = 3;

    private final AgendaDeCineDirectorioService directorioService;
    private final CinemaRepository cinemaRepository;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    // Espera entre intentos fallidos al arrancar. Visible solo para poder probarlo sin esperar minutos.
    long esperaEntreIntentosMs = 2 * 60 * 1000L;

    public DirectorioSyncScheduler(AgendaDeCineDirectorioService directorioService,
                                   CinemaRepository cinemaRepository) {
        this.directorioService = directorioService;
        this.cinemaRepository = cinemaRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        Thread hilo = new Thread(this::sincronizarAlArrancarSiHaceFalta, "directorio-sync-arranque");
        hilo.setDaemon(true);
        hilo.start();
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void cadaNoche() {
        sincronizar("nocturna");
    }

    // Solo sincroniza si en la base no hay ningún cine que venga del directorio.
    // (Filas viejas, sin agenda_de_cine_complex_id, no cuentan.)
    void sincronizarAlArrancarSiHaceFalta() {
        if (yaTieneElDirectorio()) {
            log.info("Directorio de cines: ya está cargado, no hace falta sincronizar al arrancar.");
            return;
        }
        for (int intento = 1; intento <= INTENTOS_AL_ARRANCAR; intento++) {
            if (sincronizar("arranque, intento " + intento + " de " + INTENTOS_AL_ARRANCAR)) return;
            if (intento < INTENTOS_AL_ARRANCAR) {
                try {
                    Thread.sleep(esperaEntreIntentosMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.warn("Directorio de cines: no se pudo cargar al arrancar. Se vuelve a intentar en la corrida de las 4:00.");
    }

    boolean yaTieneElDirectorio() {
        return cinemaRepository.findAll().stream().anyMatch(c -> c.getAgendaDeCineComplexId() != null);
    }

    // Devuelve true si salió bien. Nunca lanza excepciones: un fallo no debe tumbar nada.
    boolean sincronizar(String motivo) {
        if (!enCurso.compareAndSet(false, true)) {
            log.info("Sincronización del directorio ({}): ya hay otra en curso, se saltea.", motivo);
            return true;
        }
        try {
            AgendaDeCineDirectorioService.ResultadoSync r = directorioService.sincronizar();
            if (r.total() == 0) {
                log.warn("Sincronización del directorio ({}): la fuente no devolvió ningún cine.", motivo);
                return false;
            }
            log.info("Sincronización del directorio ({}): {} creados, {} actualizados, {} en total.",
                    motivo, r.creados(), r.actualizados(), r.total());
            return true;
        } catch (Exception e) {
            log.warn("Sincronización del directorio ({}) falló: {}", motivo, e.toString());
            return false;
        } finally {
            enCurso.set(false);
        }
    }
}
