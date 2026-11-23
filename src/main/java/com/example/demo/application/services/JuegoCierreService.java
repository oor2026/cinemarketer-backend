package com.example.demo.application.services;

import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.application.services.JuegoRankingService.Fila;
import com.example.demo.domain.juego.*;
import com.example.demo.domain.user.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Plataforma de juegos (admin): ranking, descalificaciones y ganadores.
 *
 * Flujo del cierre:
 * 1. La campaña cierra sola (CERRADA) al llegar la fecha de cierre.
 * 2. El admin revisa el ranking y, si hace falta, descalifica a alguien.
 * 3. "Proponer ganadores" toma el 1° como ganador y los siguientes como
 *    suplentes (PROPUESTO). Se puede volver a proponer las veces que haga falta.
 * 4. "Publicar ganadores": la campaña pasa a PUBLICADA, el ganador queda
 *    NOTIFICADO con su plazo para reclamar y los suplentes EN_ESPERA.
 * 5. Si el plazo vence sin reclamo, el premio pasa solo al siguiente suplente
 *    (tarea programada cada hora). El admin también puede pasarlo a mano.
 */
@Service
public class JuegoCierreService {

    private final JuegoAdminService adminService;
    private final JuegoRankingService rankingService;
    private final JuegoAvisosService avisos;
    private final JuegoCampaniaRepository campaniaRepository;
    private final JuegoGanadorRepository ganadorRepository;
    private final JuegoDescalificacionRepository descalificacionRepository;

    public JuegoCierreService(JuegoAdminService adminService,
                              JuegoRankingService rankingService,
                              JuegoAvisosService avisos,
                              JuegoCampaniaRepository campaniaRepository,
                              JuegoGanadorRepository ganadorRepository,
                              JuegoDescalificacionRepository descalificacionRepository) {
        this.adminService = adminService;
        this.rankingService = rankingService;
        this.avisos = avisos;
        this.campaniaRepository = campaniaRepository;
        this.ganadorRepository = ganadorRepository;
        this.descalificacionRepository = descalificacionRepository;
    }

    // Para el admin: nombre completo y mail, porque tiene que poder contactarlos.
    public record FilaAdmin(Integer posicion, Long userId, String nombre, String email, String avatar,
                            int aciertos, long tiempoMs, int diasJugados, String motivoDescalificacion) {}

    public record GanadorAdmin(Long id, int puesto, Long userId, String nombre, String email, String rol,
                               String estado, String motivo, LocalDateTime notificadoAt,
                               LocalDateTime reclamarHasta) {}

    public record Cierre(String estadoCampania, List<FilaAdmin> ranking, List<GanadorAdmin> ganadores) {}

    public record DescalificarRequest(Long userId, String motivo) {}

    public record MotivoRequest(String motivo) {}

    // ===================================================================
    // Consulta
    // ===================================================================
    @Transactional
    public Cierre cierre(Long campaniaId) {
        JuegoCampania c = adminService.buscar(campaniaId);
        adminService.sincronizarEstado(c);
        List<Fila> filas = rankingService.calcular(c.getId());
        List<JuegoGanador> ganadores = ganadorRepository.findByCampaniaIdOrderByPuestoAsc(c.getId());

        Set<Long> ids = new HashSet<>();
        filas.forEach(f -> ids.add(f.userId()));
        ganadores.forEach(g -> ids.add(g.getUserId()));
        Map<Long, User> usuarios = rankingService.usuarios(ids);

        List<FilaAdmin> ranking = new ArrayList<>();
        for (Fila f : filas) {
            User u = usuarios.get(f.userId());
            ranking.add(new FilaAdmin(f.posicion(), f.userId(), nombre(u), u == null ? null : u.getEmail(),
                    JuegoRankingService.avatar(u), f.aciertos(), f.tiempoMs(), f.diasJugados(),
                    f.motivoDescalificacion()));
        }
        List<GanadorAdmin> lista = new ArrayList<>();
        for (JuegoGanador g : ganadores) {
            User u = usuarios.get(g.getUserId());
            lista.add(new GanadorAdmin(g.getId(), g.getPuesto(), g.getUserId(), nombre(u),
                    u == null ? null : u.getEmail(), g.getRol(), g.getEstado(), g.getMotivo(),
                    g.getNotificadoAt(), g.getReclamarHasta()));
        }
        return new Cierre(c.getEstado(), ranking, lista);
    }

    // ===================================================================
    // Descalificaciones (hasta publicar los ganadores)
    // ===================================================================
    @Transactional
    public Cierre descalificar(Long campaniaId, DescalificarRequest req, Long adminId) {
        JuegoCampania c = campaniaAntesDePublicar(campaniaId);
        if (req == null || req.userId() == null) throw new JuegoException(400, "Falta el participante");
        String motivo = req.motivo() == null ? "" : req.motivo().trim();
        if (motivo.isEmpty()) throw new JuegoException(400, "Escribí el motivo de la descalificación");
        if (motivo.length() > 300) throw new JuegoException(400, "El motivo puede tener hasta 300 caracteres");
        if (descalificacionRepository.findByCampaniaIdAndUserId(c.getId(), req.userId()).isEmpty()) {
            JuegoDescalificacion d = new JuegoDescalificacion();
            d.setCampaniaId(c.getId());
            d.setUserId(req.userId());
            d.setMotivo(motivo);
            d.setAdminId(adminId);
            d.setCreatedAt(adminService.ahora());
            descalificacionRepository.save(d);
        }
        borrarPropuesta(c); // el ranking cambió: hay que volver a proponer
        return cierre(c.getId());
    }

    @Transactional
    public Cierre rehabilitar(Long campaniaId, Long userId) {
        JuegoCampania c = campaniaAntesDePublicar(campaniaId);
        descalificacionRepository.findByCampaniaIdAndUserId(c.getId(), userId)
                .ifPresent(descalificacionRepository::delete);
        borrarPropuesta(c);
        return cierre(c.getId());
    }

    // ===================================================================
    // Ganadores
    // ===================================================================
    @Transactional
    public Cierre proponer(Long campaniaId) {
        JuegoCampania c = adminService.buscar(campaniaId);
        adminService.sincronizarEstado(c);
        if (!JuegoCampania.CERRADA.equals(c.getEstado())) {
            throw new JuegoException(409, "Los ganadores se proponen cuando la campaña ya cerró");
        }
        List<Fila> elegibles = rankingService.calcular(c.getId()).stream()
                .filter(f -> !f.descalificado() && f.aciertos() > 0)
                .limit(1 + c.getCantidadSuplentes())
                .toList();
        if (elegibles.isEmpty()) throw new JuegoException(409, "No hay participantes con aciertos");

        borrarPropuesta(c);
        LocalDateTime ahora = adminService.ahora();
        int puesto = 1;
        for (Fila f : elegibles) {
            JuegoGanador g = new JuegoGanador();
            g.setCampaniaId(c.getId());
            g.setUserId(f.userId());
            g.setPuesto(puesto);
            g.setRol(puesto == 1 ? JuegoGanador.GANADOR : JuegoGanador.SUPLENTE);
            g.setEstado(JuegoGanador.PROPUESTO);
            g.setCreatedAt(ahora);
            ganadorRepository.save(g);
            puesto++;
        }
        return cierre(c.getId());
    }

    @Transactional
    public Cierre publicarGanadores(Long campaniaId) {
        JuegoCampania c = adminService.buscar(campaniaId);
        adminService.sincronizarEstado(c);
        if (!JuegoCampania.CERRADA.equals(c.getEstado())) {
            throw new JuegoException(409, "Solo se publican ganadores de una campaña cerrada");
        }
        List<JuegoGanador> lista = ganadorRepository.findByCampaniaIdOrderByPuestoAsc(c.getId());
        if (lista.isEmpty()) throw new JuegoException(409, "Primero proponé los ganadores");

        LocalDateTime ahora = adminService.ahora();
        for (JuegoGanador g : lista) {
            if (g.getPuesto() == 1) {
                notificar(c, g, ahora);
            } else {
                g.setEstado(JuegoGanador.EN_ESPERA);
                ganadorRepository.save(g);
            }
        }
        c.setEstado(JuegoCampania.PUBLICADA);
        campaniaRepository.save(c);
        return cierre(c.getId());
    }

    @Transactional
    public Cierre marcarEntregado(Long ganadorId) {
        JuegoGanador g = buscarGanador(ganadorId);
        if (!JuegoGanador.NOTIFICADO.equals(g.getEstado()) && !JuegoGanador.RECLAMADO.equals(g.getEstado())) {
            throw new JuegoException(409, "Solo se entrega un premio notificado o reclamado");
        }
        g.setEstado(JuegoGanador.ENTREGADO);
        ganadorRepository.save(g);
        return cierre(g.getCampaniaId());
    }

    // El admin saca al que tiene el premio (no respondió, no cumple las bases...)
    // y el premio pasa al siguiente suplente.
    @Transactional
    public Cierre pasarAlSuplente(Long ganadorId, MotivoRequest req) {
        JuegoGanador g = buscarGanador(ganadorId);
        if (!JuegoGanador.NOTIFICADO.equals(g.getEstado()) && !JuegoGanador.RECLAMADO.equals(g.getEstado())) {
            throw new JuegoException(409, "Solo se pasa al suplente el premio de quien lo tiene ahora");
        }
        String motivo = req == null || req.motivo() == null ? "" : req.motivo().trim();
        if (motivo.isEmpty()) throw new JuegoException(400, "Escribí el motivo");
        if (motivo.length() > 300) motivo = motivo.substring(0, 300);
        g.setEstado(JuegoGanador.DESCARTADO);
        g.setMotivo(motivo);
        ganadorRepository.save(g);
        pasarAlSiguiente(adminService.buscar(g.getCampaniaId()), adminService.ahora());
        return cierre(g.getCampaniaId());
    }

    // ===================================================================
    // Tarea programada: estados por fecha y plazos vencidos (cada hora)
    // ===================================================================
    @Scheduled(cron = "0 2 * * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void tareaHoraria() {
        for (JuegoCampania c : campaniaRepository.findByEstadoIn(List.of(JuegoCampania.PROGRAMADA, JuegoCampania.ACTIVA))) {
            adminService.sincronizarEstado(c);
        }
        vencerPlazos();
    }

    @Transactional
    public int vencerPlazos() {
        LocalDateTime ahora = adminService.ahora();
        int vencidos = 0;
        for (JuegoGanador g : ganadorRepository.findByEstadoAndReclamarHastaBefore(JuegoGanador.NOTIFICADO, ahora)) {
            g.setEstado(JuegoGanador.VENCIDO);
            g.setMotivo("No reclamó el premio dentro del plazo");
            ganadorRepository.save(g);
            pasarAlSiguiente(adminService.buscar(g.getCampaniaId()), ahora);
            vencidos++;
        }
        return vencidos;
    }

    // ===================================================================
    // Auxiliares
    // ===================================================================
    private void pasarAlSiguiente(JuegoCampania c, LocalDateTime ahora) {
        ganadorRepository.findByCampaniaIdOrderByPuestoAsc(c.getId()).stream()
                .filter(x -> JuegoGanador.EN_ESPERA.equals(x.getEstado()))
                .findFirst()
                .ifPresent(siguiente -> notificar(c, siguiente, ahora));
    }

    private void notificar(JuegoCampania c, JuegoGanador g, LocalDateTime ahora) {
        g.setEstado(JuegoGanador.NOTIFICADO);
        g.setNotificadoAt(ahora);
        g.setReclamarHasta(ahora.plusDays(c.getDiasParaReclamar()));
        ganadorRepository.save(g);
        User u = rankingService.usuarios(List.of(g.getUserId())).get(g.getUserId());
        if (u != null) avisos.premioGanado(c, u, g);
    }

    private void borrarPropuesta(JuegoCampania c) {
        for (JuegoGanador g : ganadorRepository.findByCampaniaIdOrderByPuestoAsc(c.getId())) {
            ganadorRepository.delete(g);
        }
        // Se borra ya en la base: si no, al volver a guardar al mismo usuario
        // chocaría con la restricción única (campania_id, user_id).
        ganadorRepository.flush();
    }

    private JuegoCampania campaniaAntesDePublicar(Long campaniaId) {
        JuegoCampania c = adminService.buscar(campaniaId);
        adminService.sincronizarEstado(c);
        if (JuegoCampania.PUBLICADA.equals(c.getEstado()) || JuegoCampania.ARCHIVADA.equals(c.getEstado())) {
            throw new JuegoException(409, "Los ganadores ya se publicaron: el ranking no se puede cambiar");
        }
        if (JuegoCampania.BORRADOR.equals(c.getEstado())) {
            throw new JuegoException(409, "La campaña todavía no se jugó");
        }
        return c;
    }

    private JuegoGanador buscarGanador(Long id) {
        return ganadorRepository.findById(id).orElseThrow(() -> new JuegoException(404, "No existe ese ganador"));
    }

    private static String nombre(User u) {
        return u == null ? "(usuario borrado)" : u.getName();
    }
}
