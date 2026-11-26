package com.example.demo.application.services;

import com.example.demo.domain.juego.JuegoCampania;
import com.example.demo.domain.juego.JuegoGanador;
import com.example.demo.application.services.JuegoParticipanteService.Contacto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Avisos por mail de las campañas de juego:
 *   - premioGanado:    al ganador (o al suplente que hereda el premio), para que lo reclame.
 *   - premioReclamado: confirmación al ganador + alerta al admin con sus datos.
 * Sirve igual con o sin registro: recibe el contacto del participante (el
 * email de su cuenta, o el que escribió al anotarse, tal cual).
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

    public void premioGanado(JuegoCampania campania, Contacto contacto, JuegoGanador ganador) {
        log.info("[Juegos] Premio notificado: campaña '{}' → participante {} ({}), puesto {}, reclamar hasta {}",
                campania.getSlug(), ganador.getParticipanteId(), contacto.email(), ganador.getPuesto(),
                ganador.getReclamarHasta());
        if (sinMail(contacto)) return;
        try {
            emailService.sendJuegoPremioGanadoEmail(
                    contacto.email(), contacto.nombre(), campania.getNombre(), campania.getPremioTexto(),
                    JuegoGanador.SUPLENTE.equals(ganador.getRol()),
                    ganador.getReclamarHasta() == null ? null : FECHA.format(ganador.getReclamarHasta()),
                    campania.getSlug(), contacto.conCuenta());
        } catch (Exception e) {
            log.warn("[Juegos] No se pudo enviar el aviso de premio a {}: {}", contacto.email(), e.getMessage());
        }
    }

    public void premioReclamado(JuegoCampania campania, Contacto contacto, JuegoGanador ganador) {
        log.info("[Juegos] Premio reclamado: campaña '{}' → participante {} ({}), puesto {}",
                campania.getSlug(), ganador.getParticipanteId(), contacto.email(), ganador.getPuesto());
        if (!sinMail(contacto)) {
            try {
                emailService.sendJuegoPremioReclamadoEmail(
                        contacto.email(), contacto.nombre(), campania.getNombre(), campania.getPremioTexto(),
                        contacto.conCuenta());
            } catch (Exception e) {
                log.warn("[Juegos] No se pudo enviar la confirmación de reclamo a {}: {}", contacto.email(), e.getMessage());
            }
        }
        try {
            emailService.sendAdminJuegoReclamoAlert(
                    campania.getNombre(), campania.getPremioTexto(), ganador.getRol(), ganador.getPuesto(),
                    contacto.nombre(), contacto.email(), contacto.dni(), contacto.telefono(),
                    contacto.conCuenta());
        } catch (Exception e) {
            log.warn("[Juegos] No se pudo enviar la alerta de reclamo al admin: {}", e.getMessage());
        }
    }

    private static boolean sinMail(Contacto contacto) {
        return contacto.email() == null || contacto.email().isBlank();
    }
}
