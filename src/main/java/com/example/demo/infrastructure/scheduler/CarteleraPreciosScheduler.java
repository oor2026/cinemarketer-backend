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
     * Todos los días a las 00:00, hora Argentina. Antes era semanal, pero
     * algunas cadenas publican aumentos programados con fecha de vigencia
     * (ej. Cinemark, "a partir del 15/10"): con una actualización por
     * semana, el precio nuevo se vería hasta 6 días tarde. Además, si un
     * sitio rechaza el pedido en una corrida, se reintenta al día
     * siguiente en vez de esperar una semana. Son unos 13 pedidos por día,
     * una carga mínima para los sitios de las cadenas.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Argentina/Buenos_Aires")
    public void actualizarPreciosDiario() {
        log.info("🎬 Iniciando actualización diaria de precios de cartelera - {}", LocalDateTime.now());
        try {
            carteleraPreciosService.actualizarPrecios();
            log.info("✅ Precios de cartelera actualizados correctamente");
        } catch (Exception e) {
            log.error("❌ Error al actualizar precios de cartelera", e);
        }
    }
}
