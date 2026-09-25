package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.services.CarteleraPreciosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CarteleraPreciosScheduler {

    private static final Logger log = LoggerFactory.getLogger(CarteleraPreciosScheduler.class);

    private final CarteleraPreciosService carteleraPreciosService;

    public CarteleraPreciosScheduler(CarteleraPreciosService carteleraPreciosService) {
        this.carteleraPreciosService = carteleraPreciosService;
    }

    /**
     * Todos los lunes a las 00:00, hora Argentina — mismo criterio que
     * pediste. Los precios no cambian todos los días, así que semanal
     * alcanza; evita golpear los sitios de las cadenas sin necesidad.
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "America/Argentina/Buenos_Aires")
    public void actualizarPreciosSemanal() {
        log.info("🎬 Iniciando actualización semanal de precios de cartelera - {}", LocalDateTime.now());
        try {
            carteleraPreciosService.actualizarPrecios();
            log.info("✅ Precios de cartelera actualizados correctamente");
        } catch (Exception e) {
            log.error("❌ Error al actualizar precios de cartelera", e);
        }
    }
}
