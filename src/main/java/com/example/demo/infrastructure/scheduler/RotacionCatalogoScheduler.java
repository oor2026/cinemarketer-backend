package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.services.RotacionCatalogoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Mantiene vivo el catálogo de misiones rotativas (saga, actor/actriz, director y género):
 *   · el día 1 de cada mes a las 4:00 (hora de Argentina) lo renueva desde lo popular de TMDB;
 *   · al arrancar el servidor, 2 minutos después, revisa SIEMPRE las sagas (si se sumó una al buscador, su misión se crea sola) y
 *     completa el resto SOLO si todavía es chico (menos de 60 objetivos): así la primera vez no hay que esperar al mes siguiente.
 * El catálogo solo crece; nunca borra ni desactiva nada. Si dos instancias del servidor lo corren a la vez, la base deja pasar a una
 * sola (ver RotacionRepositorio).
 */
@Component
public class RotacionCatalogoScheduler {

    private static final Logger log = LoggerFactory.getLogger(RotacionCatalogoScheduler.class);

    static final int UMBRAL_ARRANQUE = 60;

    private final RotacionCatalogoService servicio;

    public RotacionCatalogoScheduler(RotacionCatalogoService servicio) { this.servicio = servicio; }

    @Scheduled(cron = "0 0 4 1 * *", zone = "America/Argentina/Buenos_Aires")
    public void mensual() { correr("mensual"); }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        CompletableFuture.runAsync(this::arranque, CompletableFuture.delayedExecutor(2, TimeUnit.MINUTES));
    }

    void arranque() {
        try {
            log.info("[rotación] sagas al arrancar: {}", servicio.refrescarSagas().texto());
        } catch (Exception e) {
            log.error("[rotación] no se pudieron revisar las sagas al arrancar: {}", e.getMessage());
        }
        try {
            int objetivos = servicio.cantidadObjetivos();
            if (objetivos >= UMBRAL_ARRANQUE) {
                log.info("[rotación] el catálogo ya tiene {} objetivos: no hace falta completarlo al arrancar", objetivos);
                return;
            }
            correr("arranque (" + objetivos + " objetivos)");
        } catch (Exception e) {
            log.error("[rotación] no se pudo revisar el catálogo al arrancar: {}", e.getMessage());
        }
    }

    private void correr(String motivo) {
        try {
            log.info("[rotación] actualizando el catálogo ({})...", motivo);
            log.info("[rotación] listo. {}", servicio.refrescar().texto());
        } catch (Exception e) {
            log.error("[rotación] falló la actualización del catálogo ({}): {}", motivo, e.getMessage());
        }
    }
}