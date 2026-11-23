package com.example.demo.application.services;

import com.example.demo.domain.juego.JuegoCampania;
import com.example.demo.domain.juego.JuegoGanador;
import com.example.demo.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Avisos al ganador (o al suplente que hereda el premio).
 *
 * Por ahora deja constancia en el log. El mail (Brevo) y la notificación de
 * la plataforma se conectan acá, con los servicios que ya existen, sin tocar
 * el resto del módulo. Aunque no llegue el aviso, el ganador ve su premio y
 * el botón para reclamarlo al entrar a la landing de la campaña.
 */
@Service
public class JuegoAvisosService {

    private static final Logger log = LoggerFactory.getLogger(JuegoAvisosService.class);

    public void premioGanado(JuegoCampania campania, User usuario, JuegoGanador ganador) {
        log.info("[Juegos] Premio notificado: campaña '{}' → usuario {} ({}), puesto {}, reclamar hasta {}",
                campania.getSlug(), usuario.getId(), usuario.getEmail(), ganador.getPuesto(),
                ganador.getReclamarHasta());
    }
}
