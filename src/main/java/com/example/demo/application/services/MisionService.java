package com.example.demo.application.services;

import com.example.demo.domain.mision.ActividadEvento;
import com.example.demo.domain.mision.CatalogoPeliculas;
import com.example.demo.domain.mision.MisionDatos;
import com.example.demo.domain.mision.MisionLogica;
import com.example.demo.domain.mision.MisionLogica.*;
import com.example.demo.domain.mision.PerfilUsuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Misiones y sellos. Cada misión cumplida otorga un SELLO (nunca puntos).
 *  - alRegistrarActividad: lo llaman los controladores (vía ApplicationEventPublisher) cuando el usuario vota, comenta, etc.
 *  - resumen / completo / visto / logros: lo que lee el front.
 */
@Service
public class MisionService {

    private static final Logger log = LoggerFactory.getLogger(MisionService.class);
    static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int DIAS_DE_HISTORIAL = 400;

    private final MisionDatos datos;
    private final CatalogoPeliculas catalogo;
    private Random azar = new Random();          // el sorteo de objetivos; las pruebas lo reemplazan por uno fijo

    private final PerfilUsuario perfil;
    private HistorialService historial;          // «Reclamar»: lo que el usuario ya hizo antes de las misiones (null = sin historial; las pruebas viejas no lo usan)

    @Autowired
    public MisionService(MisionDatos datos, CatalogoPeliculas catalogo, PerfilUsuario perfil) {
        this.datos = datos;
        this.catalogo = catalogo;
        this.perfil = perfil;
    }

    @Autowired(required = false)
    void usarHistorial(HistorialService historial) { this.historial = historial; }

    /** Sin datos del usuario: nadie es Premium. Para las pruebas. */
    public MisionService(MisionDatos datos, CatalogoPeliculas catalogo) { this(datos, catalogo, id -> false); }

    // ================================================================================================ Escritura
    /**
     * Corre DESPUÉS de que el voto/comentario se confirmó (AFTER_COMMIT) y en su propia transacción: si algo falla acá, el voto
     * o el comentario ya están guardados y el usuario no se entera. fallbackExecution: también corre si quien publicó no
     * estaba dentro de una transacción.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alRegistrarActividad(ActividadEvento e) {
        try {
            procesar(e, LocalDateTime.now(AR));
        } catch (Exception ex) {
            log.warn("[misiones] no se pudo procesar la actividad {} del usuario {}: {}", e.tipo(), e.userId(), ex.toString());
        }
    }

    void procesar(ActividadEvento e, LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        datos.marcarDia(e.userId(), hoy);                       // de acá sale la racha (cuenta cualquier actividad)
        Preparado p = preparar(e.userId(), ahora);              // las misiones del usuario, con su objetivo de cada familia ya asignado
        Contexto ctx = contexto(e.userId(), p, ahora);

        // Solo cuentan las misiones que el usuario tiene EN JUEGO (en curso). Con la rotación el catálogo puede tener cientos de objetivos,
        // pero cada usuario tiene a lo sumo uno por familia: así no se consulta a TMDB por todos.
        List<Mision> enJuego = p.misiones().stream().filter(m -> MisionLogica.estado(m, ctx) == Estado.EN_CURSO).toList();

        // Género, director, saga y elenco se consultan a TMDB SOLO si la actividad es sobre una película (voto, comentario o recomendación) y hay una misión
        // en juego que lo pide. Los demás eventos (gustos, canjes, publicaciones...) traen en targetId otra cosa y nunca se consultan.
        Set<Long> generos = Set.of(), directores = Set.of(), sagas = Set.of(), elenco = Set.of();
        if (sobrePelicula(e)) {
            if (enJuego.stream().anyMatch(m -> m.generoId() != null)) generos = catalogo.generos(e.targetId());
            if (enJuego.stream().anyMatch(m -> m.directorId() != null)) directores = catalogo.directores(e.targetId());
            Set<Long> candidatas = new HashSet<>();
            enJuego.forEach(m -> { if (m.sagaId() != null) candidatas.add(m.sagaId()); });
            if (!candidatas.isEmpty()) sagas = catalogo.sagas(e.targetId(), candidatas);
            if (enJuego.stream().anyMatch(m -> m.actorId() != null)) elenco = catalogo.elenco(e.targetId());
        }
        for (Mision m : enJuego) {
            if (!MisionLogica.coincide(m, e, generos, directores, sagas, elenco)) continue;
            avanzar(e, m, ctx, ahora);
        }
    }

    /** ¿El targetId del evento es el id de una película de TMDB? */
    private static boolean sobrePelicula(ActividadEvento e) {
        return e.contenido() == ActividadEvento.Contenido.PELICULA && e.targetId() != null
                && (e.tipo() == ActividadEvento.Tipo.VOTO || e.tipo() == ActividadEvento.Tipo.COMENTARIO || e.tipo() == ActividadEvento.Tipo.RECOMENDACION);
    }

    private void avanzar(ActividadEvento e, Mision m, Contexto ctx, LocalDateTime ahora) {
        if (m.escaleraSobreVeces()) { avanzarRepetible(e, m, ctx, ahora); return; }
        if (m.esEscalera()) { avanzarEscalera(e, m, ctx, ahora); return; }

        // ---- misión común (sin escalera): se cumple una sola vez
        Fila f = datos.filaParaActualizar(e.userId(), m.id(), MisionLogica.ciclo(m.vigencia(), ctx.hoy()), ahora);
        if ("CUMPLIDA".equals(f.estado())) return;
        int nuevo = siguienteAvance(e, m, f, ctx);
        if (nuevo < 0) return;
        nuevo = Math.min(nuevo, m.meta());
        if (nuevo >= m.meta()) {
            if (datos.cumplir(f.id(), nuevo, ahora)) {
                datos.otorgarSello(e.userId(), m.sello().id(), m.nivelSello(), m.id(), ahora);
                datos.crearCelebracion(e.userId(), m.id(), m.nivelSello(), "NIVEL", ahora);
            }
        } else if (nuevo != f.progreso()) {
            datos.actualizarProgreso(f.id(), nuevo, ahora);
        }
    }

    /** El avance que deja esta actividad en esa fila; -1 si no suma (un título que ya estaba contado). */
    private int siguienteAvance(ActividadEvento e, Mision m, Fila f, Contexto ctx) {
        if ("RACHA_DIAS".equals(m.metrica())) return MisionLogica.racha(ctx.diasActivos(), ctx.hoy());
        if (m.cuentaDistintos() && (e.targetId() == null || !datos.contarTitulo(f.id(), e.targetId()))) return -1;   // ese título ya estaba contado
        return f.progreso() + 1;
    }

    /**
     * Escalera sobre el avance ACUMULADO. Es UNA sola fila 'P' que no se reinicia: al llegar a la meta del escalón, el sello sube de
     * nivel, se deja una celebración y la misión pasa a pedir la meta siguiente. Si la escalera no tiene paso fijo, el último escalón
     * la termina y la misión queda ganada.
     */
    private void avanzarEscalera(ActividadEvento e, Mision m, Contexto ctx, LocalDateTime ahora) {
        Fila k = datos.filaParaActualizar(e.userId(), m.id(), MisionLogica.ciclo(m.vigencia(), ctx.hoy()), ahora);
        if ("CUMPLIDA".equals(k.estado())) return;                      // la escalera ya terminó
        int nuevo = siguienteAvance(e, m, k, ctx);
        if (nuevo < 0) return;
        int nivel = k.nivel();
        while (true) {
            int meta = m.metaDelNivel(nivel);
            if (meta < 0 || nuevo < meta) break;
            nivel++;
            datos.otorgarSello(e.userId(), m.sello().id(), nivel, m.id(), ahora);
            datos.crearCelebracion(e.userId(), m.id(), nivel, "NIVEL", ahora);
        }
        if (nivel == k.nivel() && nuevo == k.progreso()) return;        // nada cambió (una racha que ya estaba en ese número)
        datos.actualizarContador(k.id(), nuevo, nivel, k.veces(), ahora);
        if (m.metaDelNivel(nivel) < 0) datos.cumplir(k.id(), nuevo, ahora);    // fin de la escalera: la misión queda ganada
    }

    /**
     * Escalera sobre las VECES que se cumplió una misión que se repite (la del día o la de la semana). El avance de hoy vive en la fila
     * del ciclo; al cerrarlo se suma una vez al contador permanente ('P'). Si esa vez completa un escalón: celebración de nivel; si no,
     * un cartelito liviano («Cumplida hoy»).
     */
    private void avanzarRepetible(ActividadEvento e, Mision m, Contexto ctx, LocalDateTime ahora) {
        Fila f = datos.filaParaActualizar(e.userId(), m.id(), MisionLogica.ciclo(m.vigencia(), ctx.hoy()), ahora);
        if ("CUMPLIDA".equals(f.estado())) return;                      // hoy (o esta semana) ya la cumplió
        int nuevo = siguienteAvance(e, m, f, ctx);
        if (nuevo < 0) return;
        nuevo = Math.min(nuevo, m.meta());
        if (nuevo < m.meta()) {
            if (nuevo != f.progreso()) datos.actualizarProgreso(f.id(), nuevo, ahora);
            return;
        }
        if (!datos.cumplir(f.id(), nuevo, ahora)) return;               // cerró el día / la semana (esto ocurre una sola vez)
        Fila k = datos.filaParaActualizar(e.userId(), m.id(), "P", ahora);      // el contador de veces
        int veces = k.veces() + 1, nivel = k.nivel();
        boolean subio = false;
        while (true) {
            int meta = m.metaDelNivel(nivel);
            if (meta < 0 || veces < meta) break;
            nivel++;
            subio = true;
            datos.otorgarSello(e.userId(), m.sello().id(), nivel, m.id(), ahora);
            datos.crearCelebracion(e.userId(), m.id(), nivel, "NIVEL", ahora);
        }
        if (!subio) datos.crearCelebracion(e.userId(), m.id(), nivel, "LIVIANA", ahora);
        datos.actualizarContador(k.id(), k.progreso(), nivel, veces, ahora);
    }

    /** Las misiones de un usuario ya con su rotación al día: lo que se necesita para mostrar o para contar una actividad. */
    private record Preparado(List<Mision> misiones, List<Asignacion> asignaciones, Map<Long, Integer> sellos) {}

    /**
     * Antes de mostrar o contar, se asegura de que el usuario tenga SU objetivo actual y el siguiente en cada familia que rota.
     * Si el actual ya se completó (tiene su sello) o dejó de existir, pasa el siguiente a actual y sortea otro. Por eso completar o
     * descartar no necesita ningún aviso aparte: la próxima vez que se mira, el sistema lo acomoda.
     */
    private Preparado preparar(long userId, LocalDateTime ahora) {
        List<Asignacion> asig = datos.asignaciones(userId);
        List<Mision> misiones = paraEsteUsuario(userId, datos.misionesActivas(userId));
        Map<Long, Integer> sellos = datos.nivelesDeSellos(userId);
        if (reconciliar(userId, asig, misiones, sellos, ahora)) {
            asig = datos.asignaciones(userId);
            misiones = paraEsteUsuario(userId, datos.misionesActivas(userId));
        }
        return new Preparado(misiones, asig, sellos);
    }

    /**
     * Saca las misiones que no le corresponden por su audiencia (solo Premium / solo los que no son Premium). Solo consulta si el usuario es Premium
     * cuando hay alguna misión con audiencia: lo normal es que todas sean para todos y no se consulta nada.
     */
    private List<Mision> paraEsteUsuario(long userId, List<Mision> misiones) {
        if (misiones.stream().allMatch(m -> m.audiencia() == null || "TODOS".equals(m.audiencia()))) return misiones;
        boolean premium;
        try { premium = perfil.esPremium(userId); } catch (Exception e) { premium = false; }
        final boolean esPremium = premium;
        return misiones.stream().filter(m -> MisionLogica.audienciaPermite(m.audiencia(), esPremium)).toList();
    }

    /** true si cambió algo. */
    private boolean reconciliar(long userId, List<Asignacion> asig, List<Mision> misiones, Map<Long, Integer> sellos, LocalDateTime ahora) {
        Map<Long, Mision> porId = new HashMap<>();
        misiones.forEach(m -> porId.put(m.id(), m));
        boolean cambio = false;
        for (String familia : MisionLogica.FAMILIAS) {
            Asignacion actual = buscar(asig, familia, "ACTUAL"), siguiente = buscar(asig, familia, "SIGUIENTE");
            String fin = cierre(actual, porId, sellos, "COMPLETADO");
            if (fin != null) { datos.cambiarEstadoObjetivo(userId, actual.objetivoId(), fin, ahora); actual = null; cambio = true; }
            fin = cierre(siguiente, porId, sellos, "CERRADO");
            if (fin != null) { datos.cambiarEstadoObjetivo(userId, siguiente.objetivoId(), fin, ahora); siguiente = null; cambio = true; }
            if (actual == null && siguiente != null) {              // la siguiente pasa a ser la actual
                datos.cambiarEstadoObjetivo(userId, siguiente.objetivoId(), "ACTUAL", ahora);
                actual = siguiente; siguiente = null; cambio = true;
            }
            if (actual == null && sortear(userId, familia, "ACTUAL", ahora)) cambio = true;
            if (siguiente == null && sortear(userId, familia, "SIGUIENTE", ahora)) cambio = true;
        }
        return cambio;
    }

    private static Asignacion buscar(List<Asignacion> asig, String familia, String estado) {
        return asig.stream().filter(a -> a.familia().equals(familia) && a.estado().equals(estado)).findFirst().orElse(null);
    }

    /** null si el objetivo sigue vigente; si no, el estado con el que se cierra: ya tiene el sello (se completó) o la misión dejó de estar activa. */
    private static String cierre(Asignacion a, Map<Long, Mision> porId, Map<Long, Integer> sellos, String siCompleto) {
        if (a == null) return null;
        Mision m = porId.get(a.misionId());
        if (m == null) return "CERRADO";
        Integer n = sellos.get(m.sello().id());
        return n != null && n >= m.nivelSello() ? siCompleto : null;
    }

    /** Sortea al azar un objetivo que todavía no se le ofreció (sin repetir hasta agotar la lista). false si ya no queda ninguno. */
    private boolean sortear(long userId, String familia, String estado, LocalDateTime ahora) {
        List<Candidato> candidatos = datos.candidatos(userId, familia);
        if (candidatos.isEmpty()) return false;
        datos.asignar(userId, familia, candidatos.get(azar.nextInt(candidatos.size())).objetivoId(), estado, ahora);
        return true;
    }

    void usarAzar(Random r) { this.azar = r; }

    private Contexto contexto(long userId, Preparado p, LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        Map<Long, Mision> porId = new HashMap<>();
        p.misiones().forEach(m -> porId.put(m.id(), m));
        Map<Long, String> asignaciones = new HashMap<>();
        p.asignaciones().forEach(a -> asignaciones.put(a.misionId(), a.estado()));
        List<Fila> filas = datos.filas(userId, MisionLogica.ciclo("HOY", hoy), MisionLogica.ciclo("SEMANA", hoy));
        return new Contexto(p.sellos(), MisionLogica.filasActuales(filas, p.misiones(), hoy), MisionLogica.contadores(filas), asignaciones,
                datos.diasActivos(userId, hoy.minusDays(DIAS_DE_HISTORIAL)), hoy, ahora, porId);
    }

    // ================================================================================================ Lectura
    @Transactional
    public Map<String, Object> resumen(long userId) { return resumen(userId, LocalDateTime.now(AR)); }

    Map<String, Object> resumen(long userId, LocalDateTime ahora) {
        Preparado p = preparar(userId, ahora);
        Contexto previo = contexto(userId, p, ahora);
        HistorialService.Revision rev = revisarHistorial(userId, p, previo, ahora);
        Contexto ctx = rev.cambio() ? contexto(userId, p, ahora) : previo;          // si el historial sumó avance, se vuelve a leer
        List<Vista> vistas = sinReclamables(MisionLogica.vistas(p.misiones(), ctx), rev);
        Optional<Vista> destacada = MisionLogica.destacada(vistas);
        Map<String, Object> r = new LinkedHashMap<>();
        destacada.ifPresent(v -> r.put("destacada", MisionLogica.dto(v, ctx, false)));
        if (!rev.reclamables().isEmpty()) r.put("porReclamar", rev.reclamables().size());
        List<Map<String, Object>> sinVer = new ArrayList<>(), avisos = new ArrayList<>();
        for (Cumplida cu : datos.cumplidasSinVer(userId)) {
            Mision m = ctx.misiones().get(cu.misionId());
            if (m == null) continue;                                   // la misión se desactivó después: se omite
            if (cu.esLiviana()) avisos.add(MisionLogica.avisoDto(cu, m, ctx));          // «Cumplida hoy»
            else sinVer.add(MisionLogica.cumplidaDto(cu, m, destacada, ctx));           // misión cumplida / subió de nivel
        }
        r.put("cumplidasSinVer", sinVer);
        if (!avisos.isEmpty()) r.put("avisos", avisos);
        return r;
    }

    @Transactional
    public Map<String, Object> completo(long userId) { return completo(userId, LocalDateTime.now(AR)); }

    Map<String, Object> completo(long userId, LocalDateTime ahora) {
        Preparado p = preparar(userId, ahora);
        Contexto previo = contexto(userId, p, ahora);
        HistorialService.Revision rev = revisarHistorial(userId, p, previo, ahora);
        Contexto ctx = rev.cambio() ? contexto(userId, p, ahora) : previo;          // si el historial sumó avance, se vuelve a leer
        List<Vista> vistas = sinReclamables(MisionLogica.vistas(p.misiones(), ctx), rev);
        Map<String, Object> r = new LinkedHashMap<>();
        if (!rev.reclamables().isEmpty()) r.put("porReclamar", rev.reclamables().stream().map(rc -> reclamableDto(rc, ctx)).toList());
        MisionLogica.destacada(vistas).ifPresent(v -> r.put("destacada", MisionLogica.dto(v, ctx, false)));
        r.put("enCurso", MisionLogica.enCurso(vistas).stream().map(v -> MisionLogica.dto(v, ctx, false)).toList());
        r.put("proximas", MisionLogica.proximas(vistas).stream().map(v -> MisionLogica.dto(v, ctx, true)).toList());
        Map<String, Object> sellos = new LinkedHashMap<>();
        sellos.put("total", ctx.totalSellos());
        sellos.put("recientes", datos.sellosGanados(userId, 6).stream().map(s -> MisionLogica.selloGanadoDto(s, false)).toList());
        sellos.put("porDescubrir", datos.porDescubrir(userId));
        r.put("sellos", sellos);
        return r;
    }

    /**
     * La ✕: el usuario saca una misión rotativa (la actual o la siguiente) y no la vuelve a ver. Pasa la que sigue, como si la hubiera
     * completado. false si esa misión no se puede descartar (no es rotativa, no es suya o ya no está asignada).
     */
    @Transactional
    public boolean descartar(long userId, long misionId) { return descartar(userId, misionId, LocalDateTime.now(AR)); }

    boolean descartar(long userId, long misionId, LocalDateTime ahora) {
        Preparado p = preparar(userId, ahora);
        Mision m = p.misiones().stream().filter(x -> x.id() == misionId).findFirst().orElse(null);
        if (m == null || !m.esRotativa()) return false;
        Asignacion a = p.asignaciones().stream().filter(x -> x.misionId() == misionId && (x.estado().equals("ACTUAL") || x.estado().equals("SIGUIENTE"))).findFirst().orElse(null);
        if (a == null) return false;
        datos.cambiarEstadoObjetivo(userId, a.objetivoId(), "DESCARTADO", ahora);
        preparar(userId, ahora);                                  // pasa la siguiente y se sortea otra
        return true;
    }

    /** Las descartadas del usuario, para que pueda recuperarlas. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> descartadas(long userId) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Asignacion a : datos.asignaciones(userId)) {
            if (!a.estado().equals("DESCARTADO")) continue;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", a.misionId()); d.put("nombre", a.nombre()); d.put("familia", a.familia());
            r.add(d);
        }
        return r;
    }

    /**
     * Recupera una descartada: vuelve a ser la ACTUAL de su familia, para que el usuario la vea enseguida donde estaba. La que estaba como actual
     * pasa a ser la siguiente, y la que estaba como siguiente se suelta (vuelve al sorteo sin pasar por «descartadas»). El avance que tuviera
     * la recuperada se conserva. false si no estaba descartada o la misión ya no está disponible para el usuario (por ejemplo, el administrador
     * la excluyó o la pausó).
     */
    @Transactional
    public boolean restaurar(long userId, long misionId) { return restaurar(userId, misionId, LocalDateTime.now(AR)); }

    boolean restaurar(long userId, long misionId, LocalDateTime ahora) {
        Preparado p = preparar(userId, ahora);
        Asignacion a = p.asignaciones().stream().filter(x -> x.misionId() == misionId && x.estado().equals("DESCARTADO")).findFirst().orElse(null);
        if (a == null || p.misiones().stream().noneMatch(m -> m.id() == misionId)) return false;
        Asignacion actual = buscar(p.asignaciones(), a.familia(), "ACTUAL"), siguiente = buscar(p.asignaciones(), a.familia(), "SIGUIENTE");
        // El orden importa: la base solo admite una ACTUAL y una SIGUIENTE por familia, así que primero se libera el lugar de la siguiente,
        // después la actual baja a siguiente y recién entonces entra la recuperada.
        if (siguiente != null) {
            datos.cambiarEstadoObjetivo(userId, siguiente.objetivoId(), "DESCARTADO", ahora);
            datos.quitarAsignacion(userId, siguiente.objetivoId());          // borra la asignación (solo borra las DESCARTADAS): vuelve al sorteo
        }
        if (actual != null) datos.cambiarEstadoObjetivo(userId, actual.objetivoId(), "SIGUIENTE", ahora);
        datos.cambiarEstadoObjetivo(userId, a.objetivoId(), "ACTUAL", ahora);
        preparar(userId, ahora);
        return true;
    }

    /**
     * «Reclamar»: el usuario ya cumplía esa misión antes de que existieran las misiones y los sellos. Pasa lo mismo que si la hubiera cumplido
     * recién (sello y pantalla de «Misión cumplida»). false si no tenía nada para reclamar ahí o ya lo había reclamado.
     */
    @Transactional
    public boolean reclamar(long userId, long misionId) { return reclamar(userId, misionId, LocalDateTime.now(AR)); }

    boolean reclamar(long userId, long misionId, LocalDateTime ahora) {
        if (historial == null) return false;
        preparar(userId, ahora);
        boolean ok = historial.reclamar(userId, misionId, ahora);
        if (ok) preparar(userId, ahora);                          // si era la misión actual de una familia, pasa la siguiente
        return ok;
    }

    /** Lo que el historial del usuario aporta al abrir sus misiones. Si falla, las misiones funcionan igual, solo que sin «Reclamar». */
    private HistorialService.Revision revisarHistorial(long userId, Preparado p, Contexto ctx, LocalDateTime ahora) {
        if (historial == null) return HistorialService.Revision.NADA;
        try {
            return historial.alAbrir(userId, p.misiones(), ctx, ahora);
        } catch (Exception e) {
            log.warn("[misiones] no se pudo revisar el historial del usuario {}: {}", userId, e.toString());
            return HistorialService.Revision.NADA;
        }
    }

    /** Lo que se puede reclamar se muestra aparte, con su botón: no se repite entre las misiones en curso ni se destaca con el avance en cero. */
    private static List<Vista> sinReclamables(List<Vista> vistas, HistorialService.Revision rev) {
        if (rev.reclamables().isEmpty()) return vistas;
        Set<Long> ids = rev.ids();
        return vistas.stream().filter(v -> !ids.contains(v.mision().id())).toList();
    }

    /** Una misión para reclamar, tal como la espera el front: completa (meta cumplida) y marcada como reclamable. */
    private static Map<String, Object> reclamableDto(HistorialService.Reclamable rc, Contexto ctx) {
        Mision m = rc.mision();
        int meta = MisionLogica.metaActual(m, ctx);
        int nivel = m.esEscalera() ? MisionLogica.nivelLogrado(m, ctx) + 1 : 0;
        Map<String, Object> d = MisionLogica.dto(new Vista(m, Estado.EN_CURSO, meta, meta, nivel, 0, 0), ctx, false);
        d.remove("descartable");                                  // no se descarta: se reclama
        d.put("reclamable", true);
        return d;
    }

    /** Marca la celebración como vista. false si no existe, no es de ese usuario o ya estaba vista. */
    @Transactional
    public boolean visto(long userId, long cumplidaId) { return datos.marcarVisto(userId, cumplidaId, LocalDateTime.now(AR)); }

    /** Mi Sala › Mis logros: los sellos que ganó el usuario y cuántos quedan por descubrir. */
    @Transactional(readOnly = true)
    public Map<String, Object> logros(long userId) {
        List<SelloGanado> ganados = datos.sellosGanados(userId, 500);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cumplidas", ganados.size());
        r.put("porDescubrir", datos.porDescubrir(userId));
        r.put("sellos", ganados.stream().map(s -> MisionLogica.selloGanadoDto(s, true)).toList());
        return r;
    }
}