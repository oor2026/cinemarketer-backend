package com.example.demo.application.services;

import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.application.services.JuegoRankingService.Fila;
import com.example.demo.application.services.JuegoRankingService.FilaPublica;
import com.example.demo.domain.juego.*;
import com.example.demo.domain.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Centro de actividad (lado público): la landing de una campaña y la partida.
 *
 * Reglas de la partida:
 * - Juega un "participante": con cuenta de Cinemarketer o, si la campaña es
 *   SIN_REGISTRO, solo con nombre y email (se identifica con un token).
 * - Un intento por persona por día. La tanda del día es la misma para todos,
 *   pero cada jugador la recibe en otro orden (y con las opciones mezcladas).
 * - La respuesta correcta nunca viaja al navegador: se compara en el servidor.
 *   Tampoco se revela después, porque la tanda del día es la misma para todos.
 * - El tiempo lo mide el servidor, desde que entrega la pregunta hasta que
 *   llega la respuesta, con tope en los segundos de la campaña y 2 segundos
 *   de tolerancia por la demora de la conexión.
 * - El intento arranca con el tiempo máximo y cada respuesta descuenta lo
 *   que sobró: si alguien cierra la página, esas preguntas cuentan como
 *   tiempo agotado. Si vuelve el mismo día, sigue desde donde quedó.
 */
@Service
public class JuegoPublicoService {

    static final long GRACIA_MS = 2000;

    public static final String SIN_JUGAR = "SIN_JUGAR";
    public static final String EN_CURSO = "EN_CURSO";
    public static final String TERMINADO = "TERMINADO";

    private final JuegoAdminService adminService;
    private final JuegoRankingService rankingService;
    private final JuegoCampaniaRepository campaniaRepository;
    private final JuegoPreguntaRepository preguntaRepository;
    private final JuegoIntentoRepository intentoRepository;
    private final JuegoRespuestaRepository respuestaRepository;
    private final JuegoGanadorRepository ganadorRepository;
    private final JuegoDescalificacionRepository descalificacionRepository;
    private final JuegoAvisosService avisos;
    private final JuegoParticipanteService participanteService;

    public JuegoPublicoService(JuegoAdminService adminService,
                               JuegoRankingService rankingService,
                               JuegoCampaniaRepository campaniaRepository,
                               JuegoPreguntaRepository preguntaRepository,
                               JuegoIntentoRepository intentoRepository,
                               JuegoRespuestaRepository respuestaRepository,
                               JuegoGanadorRepository ganadorRepository,
                               JuegoDescalificacionRepository descalificacionRepository,
                               JuegoAvisosService avisos,
                               JuegoParticipanteService participanteService) {
        this.adminService = adminService;
        this.rankingService = rankingService;
        this.campaniaRepository = campaniaRepository;
        this.preguntaRepository = preguntaRepository;
        this.intentoRepository = intentoRepository;
        this.respuestaRepository = respuestaRepository;
        this.ganadorRepository = ganadorRepository;
        this.descalificacionRepository = descalificacionRepository;
        this.avisos = avisos;
        this.participanteService = participanteService;
    }

    // ===================================================================
    // Lo que viaja al navegador
    // ===================================================================
    public record CampaniaPublica(String slug, String nombre, String bajada, String estado,
                                  LocalDateTime inicio, LocalDateTime cierre,
                                  int preguntasPorDia, int segundosPorPregunta,
                                  String premioTexto, String premioImagenUrl,
                                  int cantidadSuplentes, int diasParaReclamar,
                                  String basesCondiciones, String fondoUrl, String colorAcento,
                                  String bannerMobileUrl, String fondoLateralUrl, String premioDescripcion,
                                  String modoAcceso, boolean pedirNovedades, int cantidadGanadores) {}

    public record GanadorPublico(int puesto, String nombre, String avatar, String rol, boolean tienePremio) {}

    public record MiPremio(String rol, String estado, LocalDateTime reclamarHasta, boolean puedeReclamar) {}

    public record MiEstado(Integer posicion, int aciertos, long tiempoMs, int diasJugados,
                           String hoy, int hoyAciertos, boolean descalificado, MiPremio premio,
                           String nombre) {}

    public record Landing(CampaniaPublica campania, LocalDateTime ahora, LocalDateTime proximaTanda,
                          int diaNumero, int diasTotales, int participantes,
                          List<FilaPublica> ranking, List<GanadorPublico> ganadores, MiEstado yo) {}

    public record OpcionJuego(int indice, String texto) {}

    public record PreguntaJuego(Long id, int numero, int total, String texto, String imagenUrl,
                                List<OpcionJuego> opciones, int segundos, long restanteMs) {}

    public record ResultadoDia(int aciertos, int total, long tiempoMs, Integer posicion,
                               int aciertosTotales, int diasJugados, LocalDateTime proximaTanda) {}

    public record Partida(String estado, PreguntaJuego pregunta, ResultadoDia resultado) {}

    public record ResponderRequest(Long preguntaId, Integer opcion) {}

    public record Respuesta(boolean correcta, boolean fueraDeTiempo, Partida siguiente) {}

    // ===================================================================
    // Landing
    // ===================================================================
    @Transactional
    public Landing landing(String slug, User usuario, String token) {
        JuegoCampania c = buscarVisible(slug);
        LocalDateTime ahora = adminService.ahora();
        List<LocalDate> dias = adminService.dias(c);
        int diaNumero = JuegoCampania.ACTIVA.equals(c.getEstado())
                ? dias.indexOf(ahora.toLocalDate()) + 1 : 0;

        List<Fila> filas = rankingService.calcular(c.getId());
        JuegoParticipante yo = jugadorOpcional(c, usuario, token);
        List<FilaPublica> top = rankingService.top(filas, 10, yo == null ? null : yo.getId());
        int participantes = (int) filas.stream().filter(f -> !f.descalificado()).count();

        // Con cuenta, "yo" aparece apenas hay sesión (aunque todavía no haya jugado);
        // sin registro, recién cuando se anotó (tiene token válido).
        boolean identificado = c.sinRegistro() ? yo != null : usuario != null;
        String miNombre = yo != null ? yo.getNombre() : (usuario != null ? usuario.getName() : null);
        return new Landing(publica(c), ahora, proximaTanda(c, ahora), diaNumero, dias.size(),
                participantes, top, ganadoresPublicos(c),
                identificado ? miEstado(c, yo, miNombre, filas, ahora) : null);
    }

    private MiEstado miEstado(JuegoCampania c, JuegoParticipante yo, String nombre, List<Fila> filas, LocalDateTime ahora) {
        if (yo == null) return new MiEstado(null, 0, 0, 0, SIN_JUGAR, 0, false, null, nombre);
        Fila mia = filas.stream().filter(f -> f.participanteId().equals(yo.getId())).findFirst().orElse(null);
        boolean descalificado = mia != null ? mia.descalificado()
                : descalificacionRepository.findByCampaniaIdAndParticipanteId(c.getId(), yo.getId()).isPresent();

        String hoy = SIN_JUGAR;
        int hoyAciertos = 0;
        Optional<JuegoIntento> intento = intentoRepository
                .findByCampaniaIdAndParticipanteIdAndDia(c.getId(), yo.getId(), ahora.toLocalDate());
        if (intento.isPresent()) {
            hoy = intento.get().getTerminadoAt() != null ? TERMINADO : EN_CURSO;
            hoyAciertos = intento.get().getAciertos();
        }

        MiPremio premio = ganadorRepository.findByCampaniaIdAndParticipanteId(c.getId(), yo.getId())
                .filter(g -> !JuegoGanador.PROPUESTO.equals(g.getEstado()))
                .map(g -> new MiPremio(g.getRol(), g.getEstado(), g.getReclamarHasta(),
                        JuegoGanador.NOTIFICADO.equals(g.getEstado())
                                && g.getReclamarHasta() != null && !ahora.isAfter(g.getReclamarHasta())))
                .orElse(null);

        return new MiEstado(mia == null ? null : mia.posicion(),
                mia == null ? 0 : mia.aciertos(), mia == null ? 0 : mia.tiempoMs(),
                mia == null ? 0 : mia.diasJugados(), hoy, hoyAciertos,
                descalificado, premio, nombre);
    }

    // Solo después de publicar. Muestra el puesto original de cada uno y quién
    // tiene hoy el premio (si el ganador no lo reclamó, pasa al suplente).
    private List<GanadorPublico> ganadoresPublicos(JuegoCampania c) {
        if (!JuegoCampania.PUBLICADA.equals(c.getEstado()) && !JuegoCampania.ARCHIVADA.equals(c.getEstado())) {
            return List.of();
        }
        List<JuegoGanador> lista = ganadorRepository.findByCampaniaIdOrderByPuestoAsc(c.getId());
        Map<Long, JuegoParticipante> participantes = participanteService.participantes(
                lista.stream().map(JuegoGanador::getParticipanteId).toList());
        Map<Long, User> usuarios = participanteService.usuariosDe(participantes.values());
        List<GanadorPublico> salida = new ArrayList<>();
        for (JuegoGanador g : lista) {
            if (JuegoGanador.DESCARTADO.equals(g.getEstado())) continue;
            JuegoParticipante p = participantes.get(g.getParticipanteId());
            User u = p == null || p.getUserId() == null ? null : usuarios.get(p.getUserId());
            boolean tienePremio = Set.of(JuegoGanador.NOTIFICADO, JuegoGanador.RECLAMADO, JuegoGanador.ENTREGADO)
                    .contains(g.getEstado());
            salida.add(new GanadorPublico(g.getPuesto(), JuegoRankingService.nombrePublico(p, u),
                    JuegoRankingService.avatar(u), g.getRol(), tienePremio));
        }
        return salida;
    }

    // ===================================================================
    // Partida
    // ===================================================================

    // Empieza la partida de hoy, o la retoma si quedó a medias.
    @Transactional
    public Partida iniciar(String slug, User usuario, String token) {
        JuegoCampania c = buscarVisible(slug);
        exigirEnJuego(c);
        JuegoParticipante yo = jugador(c, usuario, token, true);
        exigirNoDescalificado(c, yo);
        LocalDateTime ahora = adminService.ahora();
        LocalDate hoy = ahora.toLocalDate();

        List<JuegoPregunta> tanda = preguntaRepository.findByCampaniaIdAndDiaAsignadoOrderByOrdenEnDiaAsc(c.getId(), hoy);
        if (tanda.isEmpty()) throw new JuegoException(409, "Hoy no hay preguntas cargadas. Volvé mañana.");

        JuegoIntento intento = intentoRepository.findByCampaniaIdAndParticipanteIdAndDia(c.getId(), yo.getId(), hoy)
                .orElse(null);
        if (intento == null) {
            intento = new JuegoIntento();
            intento.setCampaniaId(c.getId());
            intento.setParticipanteId(yo.getId());
            intento.setDia(hoy);
            intento.setIniciadoAt(ahora);
            intento.setAciertos(0);
            intento.setTiempoTotalMs((long) tanda.size() * limiteMs(c));
            try {
                intento = intentoRepository.saveAndFlush(intento);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Dos pedidos a la vez (por ejemplo, dos pestañas): el otro ya la creó.
                throw new JuegoException(409, "Ya tenés una partida abierta. Recargá la página.");
            }
        }
        return siguiente(c, intento, tanda, ahora);
    }

    @Transactional
    public Respuesta responder(String slug, User usuario, String token, ResponderRequest req) {
        if (req == null || req.preguntaId() == null) throw new JuegoException(400, "Falta la pregunta");
        JuegoCampania c = buscarVisible(slug);
        LocalDateTime ahora = adminService.ahora();
        if (ahora.isAfter(c.getCierre().plus(Duration.ofMillis(GRACIA_MS)))) {
            throw new JuegoException(409, "La campaña ya cerró");
        }

        JuegoParticipante yo = jugador(c, usuario, token, false);
        JuegoIntento intento = intentoRepository
                .findByCampaniaIdAndParticipanteIdAndDia(c.getId(), yo.getId(), ahora.toLocalDate())
                .orElseThrow(() -> new JuegoException(409, "La partida de ese día ya terminó. Recargá la página."));

        JuegoRespuesta pendiente = respuestaRepository.findByIntentoIdOrderByOrdenAsc(intento.getId()).stream()
                .filter(r -> r.getRespondidaAt() == null && r.getPreguntaId().equals(req.preguntaId()))
                .findFirst()
                .orElseThrow(() -> new JuegoException(409, "Esa pregunta ya fue respondida"));
        JuegoPregunta pregunta = preguntaRepository.findById(pendiente.getPreguntaId())
                .orElseThrow(() -> new JuegoException(404, "No existe la pregunta"));

        long limite = limiteMs(c);
        long transcurrido = Duration.between(pendiente.getEntregadaAt(), ahora).toMillis();
        boolean fueraDeTiempo = transcurrido > limite + GRACIA_MS;
        Integer opcion = fueraDeTiempo ? null : req.opcion();
        boolean correcta = opcion != null && opcion.equals(pregunta.getIndiceCorrecta());
        long tiempo = Math.max(0, Math.min(transcurrido, limite));

        if (respuestaRepository.marcarRespondida(pendiente.getId(), ahora, opcion, correcta, tiempo) == 0) {
            throw new JuegoException(409, "Esa pregunta ya fue respondida");
        }
        pendiente.setRespondidaAt(ahora);
        pendiente.setOpcionElegida(opcion);
        pendiente.setCorrecta(correcta);
        pendiente.setTiempoMs(tiempo);

        if (correcta) intento.setAciertos(intento.getAciertos() + 1);
        intento.setTiempoTotalMs(intento.getTiempoTotalMs() - (limite - tiempo));
        intentoRepository.save(intento);

        List<JuegoPregunta> tanda = preguntaRepository
                .findByCampaniaIdAndDiaAsignadoOrderByOrdenEnDiaAsc(c.getId(), intento.getDia());
        return new Respuesta(correcta, fueraDeTiempo, siguiente(c, intento, tanda, ahora));
    }

    // Entrega la próxima pregunta (o la que está en pantalla), o cierra el día.
    private Partida siguiente(JuegoCampania c, JuegoIntento intento, List<JuegoPregunta> tanda, LocalDateTime ahora) {
        if (intento.getTerminadoAt() != null) return terminada(c, intento, tanda.size(), ahora);
        long limite = limiteMs(c);
        List<JuegoRespuesta> respuestas = respuestaRepository.findByIntentoIdOrderByOrdenAsc(intento.getId());
        Map<Long, JuegoPregunta> porId = new HashMap<>();
        tanda.forEach(p -> porId.put(p.getId(), p));

        // La que quedó en pantalla: si todavía tiene tiempo, se vuelve a mostrar;
        // si no, cuenta como tiempo agotado (ya estaba descontado del total).
        for (JuegoRespuesta r : respuestas) {
            if (r.getRespondidaAt() != null) continue;
            long transcurrido = Duration.between(r.getEntregadaAt(), ahora).toMillis();
            if (transcurrido <= limite && porId.containsKey(r.getPreguntaId())) {
                return new Partida(EN_CURSO, armar(c, intento, porId.get(r.getPreguntaId()), r.getOrden(),
                        tanda.size(), limite - transcurrido), null);
            }
            respuestaRepository.marcarRespondida(r.getId(), ahora, null, false, limite);
            r.setRespondidaAt(ahora);
        }

        Set<Long> entregadas = new HashSet<>();
        respuestas.forEach(r -> entregadas.add(r.getPreguntaId()));
        JuegoPregunta proxima = ordenDelJugador(intento, tanda).stream()
                .filter(p -> !entregadas.contains(p.getId()))
                .findFirst().orElse(null);

        if (proxima == null) {
            intento.setTerminadoAt(ahora);
            intentoRepository.save(intento);
            return terminada(c, intento, tanda.size(), ahora);
        }

        JuegoRespuesta nueva = new JuegoRespuesta();
        nueva.setIntentoId(intento.getId());
        nueva.setPreguntaId(proxima.getId());
        nueva.setOrden(respuestas.size() + 1);
        nueva.setEntregadaAt(ahora);
        nueva.setCorrecta(false);
        respuestaRepository.save(nueva);
        return new Partida(EN_CURSO, armar(c, intento, proxima, nueva.getOrden(), tanda.size(), limite), null);
    }

    private Partida terminada(JuegoCampania c, JuegoIntento intento, int total, LocalDateTime ahora) {
        List<Fila> filas = rankingService.calcular(c.getId());
        Fila mia = filas.stream().filter(f -> f.participanteId().equals(intento.getParticipanteId())).findFirst().orElse(null);
        return new Partida(TERMINADO, null, new ResultadoDia(intento.getAciertos(), total,
                intento.getTiempoTotalMs(), mia == null ? null : mia.posicion(),
                mia == null ? intento.getAciertos() : mia.aciertos(),
                mia == null ? 1 : mia.diasJugados(), proximaTanda(c, ahora)));
    }

    // El orden de la tanda para este jugador: siempre el mismo para el mismo intento.
    private List<JuegoPregunta> ordenDelJugador(JuegoIntento intento, List<JuegoPregunta> tanda) {
        List<JuegoPregunta> orden = new ArrayList<>(tanda);
        orden.sort(Comparator.comparing(JuegoPregunta::getId));
        Collections.shuffle(orden, new Random(intento.getId() * 7919L));
        return orden;
    }

    // La pregunta sin la correcta, con las opciones mezcladas. Cada opción
    // conserva su número original, que es lo que vuelve en la respuesta.
    private PreguntaJuego armar(JuegoCampania c, JuegoIntento intento, JuegoPregunta p, int numero,
                               int total, long restanteMs) {
        List<OpcionJuego> opciones = new ArrayList<>();
        List<String> textos = p.opciones();
        for (int i = 0; i < textos.size(); i++) opciones.add(new OpcionJuego(i + 1, textos.get(i)));
        Collections.shuffle(opciones, new Random(intento.getId() * 31L + p.getId()));
        return new PreguntaJuego(p.getId(), numero, total, p.getTexto(), p.getImagenUrl(), opciones,
                c.getSegundosPorPregunta(), Math.max(0, restanteMs));
    }

    // ===================================================================
    // Premio
    // ===================================================================

    // El ganador (o el suplente que heredó el premio) confirma que lo quiere.
    @Transactional
    public MiPremio reclamar(String slug, User usuario, String token) {
        JuegoCampania c = buscarVisible(slug);
        JuegoParticipante yo = jugadorOpcional(c, usuario, token);
        if (yo == null) {
            if (c.sinRegistro()) throw new JuegoException(401, "Anotate con el mismo nombre y email con que jugaste");
            if (usuario == null) throw new JuegoException(401, "Ingresá a tu cuenta para reclamar tu premio");
            throw new JuegoException(404, "No tenés un premio en esta campaña");
        }
        JuegoGanador g = ganadorRepository.findByCampaniaIdAndParticipanteId(c.getId(), yo.getId())
                .orElseThrow(() -> new JuegoException(404, "No tenés un premio en esta campaña"));
        LocalDateTime ahora = adminService.ahora();
        if (JuegoGanador.RECLAMADO.equals(g.getEstado()) || JuegoGanador.ENTREGADO.equals(g.getEstado())) {
            return new MiPremio(g.getRol(), g.getEstado(), g.getReclamarHasta(), false);
        }
        if (!JuegoGanador.NOTIFICADO.equals(g.getEstado())) {
            throw new JuegoException(409, "Este premio no está disponible para reclamar");
        }
        if (g.getReclamarHasta() != null && ahora.isAfter(g.getReclamarHasta())) {
            throw new JuegoException(409, "Se venció el plazo para reclamar el premio");
        }
        g.setEstado(JuegoGanador.RECLAMADO);
        ganadorRepository.save(g);
        avisos.premioReclamado(c, participanteService.contacto(yo.getId()), g);
        return new MiPremio(g.getRol(), g.getEstado(), g.getReclamarHasta(), false);
    }

    // ===================================================================
    // Sin registro: anotarse con nombre y email
    // ===================================================================
    @Transactional
    public JuegoParticipanteService.Anotado anotarse(String slug, JuegoParticipanteService.AnotarseRequest req) {
        return participanteService.anotarse(buscarVisible(slug), req);
    }

    // ===================================================================
    // Auxiliares
    // ===================================================================

    // Quién juega: sin registro, el del token; con cuenta, el participante del usuario
    // (se crea al iniciar la primera partida).
    private JuegoParticipante jugador(JuegoCampania c, User usuario, String token, boolean crear) {
        if (c.sinRegistro()) {
            JuegoParticipante p = participanteService.porToken(c, token);
            if (p == null) throw new JuegoException(401, "Anotate con tu nombre y email para jugar");
            return p;
        }
        if (usuario == null) throw new JuegoException(401, "Ingresá a tu cuenta para jugar");
        if (crear) return participanteService.deUsuarioOCrear(c, usuario);
        JuegoParticipante p = participanteService.deUsuario(c, usuario);
        if (p == null) throw new JuegoException(409, "La partida de ese día ya terminó. Recargá la página.");
        return p;
    }

    private JuegoParticipante jugadorOpcional(JuegoCampania c, User usuario, String token) {
        return c.sinRegistro() ? participanteService.porToken(c, token) : participanteService.deUsuario(c, usuario);
    }

    // Una campaña en borrador no existe para el público.
    JuegoCampania buscarVisible(String slug) {
        JuegoCampania c = campaniaRepository.findBySlug(slug == null ? "" : slug.trim().toLowerCase())
                .filter(x -> !JuegoCampania.BORRADOR.equals(x.getEstado()))
                .orElseThrow(() -> new JuegoException(404, "No encontramos esta actividad"));
        if (JuegoCampania.ARCHIVADA.equals(c.getEstado()) && Boolean.TRUE.equals(c.getLandingOculta())) {
            throw new JuegoException(410, "Parece que la campaña ya finalizó");
        }
        adminService.sincronizarEstado(c);
        return c;
    }

    private void exigirEnJuego(JuegoCampania c) {
        switch (c.getEstado()) {
            case JuegoCampania.ACTIVA -> { }
            case JuegoCampania.PROGRAMADA -> throw new JuegoException(409, "La actividad todavía no empezó");
            default -> throw new JuegoException(409, "La actividad ya terminó");
        }
    }

    private void exigirNoDescalificado(JuegoCampania c, JuegoParticipante yo) {
        if (descalificacionRepository.findByCampaniaIdAndParticipanteId(c.getId(), yo.getId()).isPresent()) {
            throw new JuegoException(403, "Tu participación en esta actividad fue anulada. Escribinos a soporte si creés que es un error.");
        }
    }

    // Cuándo sale la próxima tanda: el inicio si todavía no empezó, o la
    // medianoche si mañana sigue la campaña. Null si ya no hay más.
    LocalDateTime proximaTanda(JuegoCampania c, LocalDateTime ahora) {
        if (JuegoCampania.PROGRAMADA.equals(c.getEstado())) return c.getInicio();
        if (!JuegoCampania.ACTIVA.equals(c.getEstado())) return null;
        LocalDateTime manana = ahora.toLocalDate().plusDays(1).atStartOfDay();
        return manana.isBefore(c.getCierre()) ? manana : null;
    }

    private static long limiteMs(JuegoCampania c) {
        return c.getSegundosPorPregunta() * 1000L;
    }

    private static CampaniaPublica publica(JuegoCampania c) {
        return new CampaniaPublica(c.getSlug(), c.getNombre(), c.getBajada(), c.getEstado(),
                c.getInicio(), c.getCierre(), c.getPreguntasPorDia(), c.getSegundosPorPregunta(),
                c.getPremioTexto(), c.getPremioImagenUrl(), c.getCantidadSuplentes(), c.getDiasParaReclamar(),
                c.getBasesCondiciones(), c.getFondoUrl(), c.getColorAcento(),
                c.getBannerMobileUrl(), c.getFondoLateralUrl(), c.getPremioDescripcion(),
                c.getModoAcceso() == null ? JuegoCampania.MODO_CINEMARKETER : c.getModoAcceso(),
                Boolean.TRUE.equals(c.getPedirNovedades()),
                c.getCantidadGanadores() == null ? 1 : c.getCantidadGanadores());
    }
}
