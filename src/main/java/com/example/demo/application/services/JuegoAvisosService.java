package com.example.demo.application.services;

import com.example.demo.domain.juego.JuegoCampania;
import com.example.demo.domain.juego.JuegoGanador;
import com.example.demo.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Avisos por mail de las campañas de juego:
 *   - premioGanado:    al ganador (o al suplente que hereda el premio), para que lo reclame.
 *   - premioReclamado: confirmación al ganador + alerta al admin con sus datos.
 *
 * Si un mail falla, queda en el log y el flujo sigue: el ganador igual ve su
 * premio y el botón para reclamarlo al entrar a la landing de la campaña.
 */
@Service
public class JuegoAvisosService {

    private static final Logger log = LoggerFactory.getLogger(JuegoAvisosService.class);
    static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm 'hs'");

    private final EmailService emailService;

    public JuegoAvisosService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void premioGanado(JuegoCampania campania, User usuario, JuegoGanador ganador) {
        log.info("[Juegos] Premio notificado: campaña '{}' → usuario {} ({}), puesto {}, reclamar hasta {}",
                campania.getSlug(), usuario.getId(), usuario.getEmail(), ganador.getPuesto(),
                ganador.getReclamarHasta());
        if (sinMail(usuario)) return;
        try {
            emailService.sendJuegoPremioGanadoEmail(
                    usuario.getEmail(), usuario.getName(), campania.getNombre(), campania.getPremioTexto(),
                    JuegoGanador.SUPLENTE.equals(ganador.getRol()),
                    ganador.getReclamarHasta() == null ? null : FECHA.format(ganador.getReclamarHasta()),
                    campania.getSlug());
        } catch (Exception e) {
            log.warn("[Juegos] No se pudo enviar el aviso de premio a {}: {}", usuario.getEmail(), e.getMessage());
        }
    }

    public void premioReclamado(JuegoCampania campania, User usuario, JuegoGanador ganador) {
        log.info("[Juegos] Premio reclamado: campaña '{}' → usuario {} ({}), puesto {}",
                campania.getSlug(), usuario.getId(), usuario.getEmail(), ganador.getPuesto());
        if (!sinMail(usuario)) {
            try {
                emailService.sendJuegoPremioReclamadoEmail(
                        usuario.getEmail(), usuario.getName(), campania.getNombre(), campania.getPremioTexto());
            } catch (Exception e) {
                log.warn("[Juegos] No se pudo enviar la confirmación de reclamo a {}: {}", usuario.getEmail(), e.getMessage());
            }
        }
        try {
            emailService.sendAdminJuegoReclamoAlert(
                    campania.getNombre(), campania.getPremioTexto(), ganador.getRol(), ganador.getPuesto(),
                    usuario.getName(), usuario.getEmail(), usuario.getDni(), usuario.getPhone());
        } catch (Exception e) {
            log.warn("[Juegos] No se pudo enviar la alerta de reclamo al admin: {}", e.getMessage());
        }
    }

    private static boolean sinMail(User usuario) {
        return usuario.getEmail() == null || usuario.getEmail().isBlank();
    }
}
