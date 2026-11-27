package com.example.demo.application.services;

import com.example.demo.domain.mision.CatalogoPeliculas;
import com.example.demo.domain.mision.HistorialDatos;
import com.example.demo.domain.mision.HistorialDatos.Evento;
import com.example.demo.domain.mision.HistorialDatos.Registro;
import com.example.demo.domain.mision.HistorialLogica;
import com.example.demo.domain.mision.HistorialLogica.Atributos;
import com.example.demo.domain.mision.HistorialLogica.Conteo;
import com.example.demo.domain.mision.MisionDatos;
import com.example.demo.domain.mision.MisionLogica;
import com.example.demo.domain.mision.MisionLogica.Contexto;
import com.example.demo.domain.mision.MisionLogica.Estado;
import com.example.demo.domain.mision.MisionLogica.Fila;
import com.example.demo.domain.mision.MisionLogica.Mision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * «Reclamar»: tiene en cuenta lo que el usuario ya hizo antes de que existieran las misiones y los sellos, sin regalarle nada sin preguntar.
 *
 *  - Si su historial YA cumple una misión, la misión le aparece con «Reclamar». Al tocarlo, pasa lo mismo que si la hubiera cumplido recién.
 *  - Si su historial es PARCIAL (votó 2 de las 4 películas de Matrix), ese avance se suma solo a la misión, sin botón.
 *  - Una misión rotativa que no le tocó pero que su historial ya completó (Matrix) también se le ofrece para reclamar.
 *
 * Qué cuenta como historial: lo anterior a la primera actividad que el sistema contó en vivo para esa misión (ver HistorialLogica), así que
 * nunca se suma dos veces. Cada misión se evalúa una sola vez por usuario (queda en usuario_historial).
 *
 * Las misiones que piden género, director, saga o elenco necesitan consultar a TMDB una vez por película del usuario (con caché): eso se
 * hace en segundo plano, y el resultado aparece la próxima vez que el usuario abre sus misiones. Las demás se evalúan en el momento.
 */
@Service
public class HistorialService {

    private static final Logger log = LoggerFactory.getLogger(HistorialService.class);
    /** Cada cuánto se vuelve a mirar una rotativa sin asignar que el historial todavía no completaba. */
    static final Duration REPASO = Duration.ofHours(6);
    /** Tope de películas distintas que se consultan a TMDB por usuario (las más recientes). */
    static final int MAX_TITULOS = 400;

    /** Una misión que el usuario puede reclamar. base = cuánto suma su historial. */
    public record Reclamable(Mision mision, int base) {}

    /** reclamables = todo lo que tiene para reclamar · cambio = true si se sumó avance parcial a alguna misión (hay que releer el estado). */
    public record Revision(List<Reclamable> reclamables, boolean cambio) {
        public static final Revision NADA = new Revision(List.of(), false);
        public Set<Long> ids() { return reclamables.stream().map(r -> r.mision().id()).collect(Collectors.toSet()); }
    }

    private final MisionDatos datos;
    private final HistorialDatos historial;
    private final CatalogoPeliculas catalogo;
    private final Set<Long> enMarcha = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private Executor ejecutor;
    private PlatformTransactionManager manager;

    @Autowired
    public HistorialService(MisionDatos datos, HistorialDatos historial, CatalogoPeliculas catalogo) {
        this.datos = datos;
        this.historial = historial;
        this.catalogo = catalogo;
        ExecutorService uno = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "historial-misiones"); t.setDaemon(true); return t; });
        this.ejecutor = uno;
    }

    @Autowired(required = false)
    void usarTransacciones(PlatformTransactionManager manager) { this.manager = manager; }

    /** Las pruebas corren el trabajo en segundo plano en el mismo hilo, para poder comprobarlo. */
    void usarEjecutor(Executor e) { this.ejecutor = e; }

    // ================================================================================================ Al abrir las misiones
    /**
     * Lo que se hace cada vez que el usuario abre sus misiones: evalúa lo que todavía no se evaluó y devuelve lo que tiene para reclamar.
     * @param misiones las misiones del usuario (comunes y rotativas asignadas), ya con su estado calculado en {@code ctx}
     */
    public Revision alAbrir(long userId, List<Mision> misiones, Contexto ctx, LocalDateTime ahora) {
        return aislado(() -> revisar(userId, misiones, ctx, ahora));
    }

    private Revision revisar(long userId, List<Mision> misiones, Contexto ctx, LocalDateTime ahora) {
        Map<Long, Registro> registros = historial.registros(userId);



        Set<Long> conUniverso = datos.sagasConUniverso();
        List<Mision> sinTmdb = new ArrayList<>(), conTmdb = new ArrayList<>(), sagasEnJuego = new ArrayList<>();
        for (Mision m : misiones) {
            if (!HistorialLogica.elegible(m) || MisionLogica.estado(m, ctx) != Estado.EN_CURSO) continue;
            if (esDeSaga(m, conUniverso)) { sagasEnJuego.add(m); continue; }                          // las sagas se evalúan aparte, con sus votos (sagas.json)
            Registro r = registros.get(m.id());
            if (r != null && !"IGNORADO".equals(r.estado())) continue;                 // ya evaluada
            (m.necesitaTmdb() ? conTmdb : sinTmdb).add(m);
        }
        List<Mision> sinAsignar = new ArrayList<>(), sagasLibres = new ArrayList<>();
        for (Mision m : historial.rotativasParaEvaluar(userId, ahora.minus(REPASO))) {
            if (!HistorialLogica.elegible(m)) continue;
            (esDeSaga(m, conUniverso) ? sagasLibres : sinAsignar).add(m);
        }

        boolean cambio = false;
        if (!sagasEnJuego.isEmpty() || !sagasLibres.isEmpty()) cambio = evaluarSagas(userId, sagasEnJuego, sagasLibres, registros, ahora);
        if (!sinTmdb.isEmpty()) cambio |= evaluar(userId, sinTmdb, List.of(), historial.eventos(userId), id -> Atributos.VACIO, ahora);

        List<Mision> aparte = new ArrayList<>(conTmdb);
        aparte.addAll(sinAsignar);
        if (!aparte.isEmpty() && enMarcha.add(userId)) {
            final List<Mision> comunes = List.copyOf(conTmdb), libres = List.copyOf(sinAsignar);
            ejecutor.execute(() -> {
                try { aislado(() -> { evaluarConTmdb(userId, comunes, libres, ahora); return null; }); }
                catch (Exception e) { log.warn("[misiones] no se pudo revisar el historial del usuario {}: {}", userId, e.toString()); }
                finally { enMarcha.remove(userId); }
            });
        }
        return new Revision(pendientes(userId, misiones), cambio);
    }

    /**
     * Misión de una saga rotativa cuyo universo de películas (sagas.json) ya está guardado: su avance es cuántas películas de la saga votó el usuario.
     * Si el universo todavía no se guardó (los primeros minutos después de desplegar), sigue el camino de siempre.
     */
    private boolean esDeSaga(Mision m, Set<Long> conUniverso) {
        return "SAGA".equals(m.familia()) && m.sagaId() != null && !m.esEscalera() && conUniverso.contains(m.sagaId());
    }

    /**
     * Las sagas no usan el historial por fecha ni consultan a TMDB: el avance es siempre cuántas películas DISTINTAS de la saga (las de sagas.json)
     * votó el usuario, sin tope de películas. Se recalcula cada vez que abre sus misiones, así también se corrige solo cualquier avance viejo.
     *  - enJuego (su saga actual y la siguiente): el avance queda igual a lo votado; si ya votó todas, se ofrece «Reclamar».
     *  - libres (sagas que no le tocaron): si ya votó todas, se ofrece «Reclamar»; si no, se ignoran (se vuelven a mirar cada 6 horas).
     * Lo ya reclamado no se toca. true si cambió el avance de alguna misión.
     */
    private boolean evaluarSagas(long userId, List<Mision> enJuego, List<Mision> libres, Map<Long, Registro> registros, LocalDateTime ahora) {
        Map<Long, Integer> votadas = datos.votadasPorSaga(userId);
        boolean cambio = false;
        for (Mision m : enJuego) {
            Registro r = registros.get(m.id());
            if (r != null && "RECLAMADO".equals(r.estado())) continue;
            int v = votadas.getOrDefault(m.sagaId(), 0);
            if (v >= m.meta()) {
                if (r == null || !"PENDIENTE".equals(r.estado())) historial.reevaluar(userId, m.id(), "PENDIENTE", m.meta(), ahora);
                continue;
            }
            if (v > 0) {
                Fila f = datos.filaParaActualizar(userId, m.id(), "P", ahora);
                if (!"CUMPLIDA".equals(f.estado()) && f.progreso() != v) { datos.actualizarProgreso(f.id(), v, ahora); cambio = true; }
            }
            if (r == null || !"APLICADO".equals(r.estado()) || r.base() != v) historial.reevaluar(userId, m.id(), "APLICADO", v, ahora);
        }
        for (Mision m : libres) {
            int v = votadas.getOrDefault(m.sagaId(), 0);
            historial.guardar(userId, m.id(), m.meta() > 0 && v >= m.meta() ? "PENDIENTE" : "IGNORADO", v, ahora);
        }
        return cambio;
    }

    /** Cuántas cosas tiene para reclamar (lo pide el feed liviano). */
    public int cantidadParaReclamar(long userId) { return historial.contarPendientes(userId); }

    private List<Reclamable> pendientes(long userId, List<Mision> misiones) {
        Map<Long, Registro> regs = historial.registros(userId);
        List<Long> ids = regs.values().stream().filter(r -> "PENDIENTE".equals(r.estado())).map(Registro::misionId).sorted().toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, Mision> porId = new HashMap<>();
        misiones.forEach(m -> porId.put(m.id(), m));
        List<Long> faltan = ids.stream().filter(id -> !porId.containsKey(id)).toList();
        historial.misionesPorIds(faltan).forEach(m -> porId.put(m.id(), m));
        List<Reclamable> r = new ArrayList<>();
        Map<Long, Integer> votadas = null;
        Set<Long> conUniverso = datos.sagasConUniverso();
        for (Long id : ids) {
            Mision m = porId.get(id);
            if (m == null) continue;
            if (esDeSaga(m, conUniverso)) {          // un «Reclamar» de saga viejo se revisa con los votos de hoy: la saga pudo sumar una película
                if (votadas == null) votadas = datos.votadasPorSaga(userId);
                int v = votadas.getOrDefault(m.sagaId(), 0);
                if (v < m.meta()) { historial.reevaluar(userId, m.id(), "IGNORADO", v, LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))); continue; }
            }
            r.add(new Reclamable(m, regs.get(id).base()));
        }
        return r;
    }

    /**
     * Corre en una transacción PROPIA: si algo falla acá (por ejemplo, si todavía no se corrió 14_historial.sql), se deshace solo esto y las
     * misiones del usuario siguen funcionando. Sin gestor de transacciones (las pruebas) corre directo.
     */
    private <T> T aislado(java.util.function.Supplier<T> trabajo) {
        if (manager == null) return trabajo.get();
        TransactionTemplate t = new TransactionTemplate(manager);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t.execute(estado -> trabajo.get());
    }

    // ================================================================================================ Evaluar
    /** En segundo plano: pide a TMDB los datos de las películas del usuario (con caché) y evalúa las misiones que los necesitan. */
    private void evaluarConTmdb(long userId, List<Mision> comunes, List<Mision> sinAsignar, LocalDateTime ahora) {
        List<Evento> eventos = historial.eventos(userId);
        List<Mision> todas = new ArrayList<>(comunes);
        todas.addAll(sinAsignar);
        boolean generos = todas.stream().anyMatch(m -> m.generoId() != null), directores = todas.stream().anyMatch(m -> m.directorId() != null),
                elenco = todas.stream().anyMatch(m -> m.actorId() != null);
        Set<Long> sagas = todas.stream().map(Mision::sagaId).filter(Objects::nonNull).collect(Collectors.toSet());

        // las películas del usuario, las más recientes primero y hasta el tope
        Map<Long, LocalDateTime> ultima = new HashMap<>();
        for (Evento e : eventos) if ("PELICULA".equals(e.contenido()) && ("VOTO".equals(e.tipo()) || "COMENTARIO".equals(e.tipo()))) ultima.merge(e.targetId(), e.cuando(), (a, b) -> a.isAfter(b) ? a : b);
        List<Long> titulos = ultima.entrySet().stream().sorted(Map.Entry.<Long, LocalDateTime>comparingByValue().reversed())
                .limit(MAX_TITULOS).map(Map.Entry::getKey).toList();

        // Las películas de una saga se miran SIEMPRE, aunque el usuario las haya votado hace mucho y queden fuera de las más recientes:
        // son pocas (la lista de la colección, que viene en caché) y así una saga no depende de cuántas películas votó en total.
        Set<Long> deSagas = new HashSet<>();
        for (Long s : sagas) deSagas.addAll(catalogo.peliculasDeSaga(s));
        List<Long> extras = ultima.keySet().stream().filter(deSagas::contains).filter(id -> !titulos.contains(id)).toList();

        Map<Long, Atributos> atributos = new HashMap<>();
        for (Long id : titulos) {
            Set<Long> g = generos ? catalogo.generos(id) : Set.of(), d = directores ? catalogo.directores(id) : Set.of(),
                    el = elenco ? catalogo.elenco(id) : Set.of(), sg = sagas.isEmpty() ? Set.of() : catalogo.sagas(id, sagas);
            atributos.put(id, new Atributos(g, d, el, sg));
        }
        for (Long id : extras) atributos.put(id, new Atributos(Set.of(), Set.of(), Set.of(), catalogo.sagas(id, sagas)));
        evaluar(userId, comunes, sinAsignar, eventos, id -> atributos.getOrDefault(id, Atributos.VACIO), ahora);
    }

    /**
     * Evalúa y guarda. true si se sumó avance parcial a alguna misión.
     * comunes: las que el usuario tiene (el sistema ya las cuenta en vivo) · sinAsignar: rotativas que no le tocaron (solo se mira si ya las completó).
     */
    private boolean evaluar(long userId, List<Mision> comunes, List<Mision> sinAsignar, List<Evento> eventos,
                            java.util.function.Function<Long, Atributos> atributos, LocalDateTime ahora) {
        Map<Long, LocalDateTime> primero = historial.primerAvanceEnVivo(userId);
        LocalDate hoy = ahora.toLocalDate();
        Map<Long, Fila> contadores = MisionLogica.contadores(datos.filas(userId, MisionLogica.ciclo("HOY", hoy), MisionLogica.ciclo("SEMANA", hoy)));
        boolean cambio = false;

        for (Mision m : comunes) {
            Conteo c = HistorialLogica.contar(m, eventos, HistorialLogica.corte(primero.get(m.id()), ahora), atributos);
            int base = c.total();
            if (base == 0) { historial.guardar(userId, m.id(), "APLICADO", 0, ahora); continue; }
            if (m.cuentaDistintos()) {          // un título que el sistema ya contó en vivo no se cuenta otra vez
                Fila f = datos.filaParaActualizar(userId, m.id(), "P", ahora);
                int nuevos = 0;
                for (Long id : c.titulos()) if (datos.contarTitulo(f.id(), id)) nuevos++;
                base = nuevos;
                contadores.put(m.id(), datos.filaParaActualizar(userId, m.id(), "P", ahora));
            }
            Fila k = contadores.get(m.id());
            int vivo = k == null ? 0 : k.progreso(), nivel = k == null ? 0 : k.nivel();
            int meta = HistorialLogica.meta(m, nivel);
            if (base > 0 && meta > 0 && vivo + base >= meta) {
                historial.guardar(userId, m.id(), "PENDIENTE", base, ahora);
            } else {
                if (base > 0) { sumarAvance(userId, m, base, ahora); cambio = true; }
                historial.guardar(userId, m.id(), "APLICADO", base, ahora);
            }
        }

        for (Mision m : sinAsignar) {
            Conteo c = HistorialLogica.contar(m, eventos, ahora, atributos);
            int meta = HistorialLogica.meta(m, 0);
            boolean completa = c.total() > 0 && meta > 0 && c.total() >= meta;
            historial.guardar(userId, m.id(), completa ? "PENDIENTE" : "IGNORADO", c.total(), ahora);
        }
        return cambio;
    }

    /** Suma un avance parcial del historial a la misión (nunca llega a completarla: eso es «Reclamar»). */
    private void sumarAvance(long userId, Mision m, int base, LocalDateTime ahora) {
        Fila f = datos.filaParaActualizar(userId, m.id(), "P", ahora);
        if ("CUMPLIDA".equals(f.estado())) return;
        if (m.esEscalera()) datos.actualizarContador(f.id(), f.progreso() + base, f.nivel(), f.veces(), ahora);
        else datos.actualizarProgreso(f.id(), Math.min(f.progreso() + base, m.meta() - 1), ahora);
    }

    // ================================================================================================ Reclamar
    /**
     * El usuario toca «Reclamar»: pasa lo mismo que si hubiera cumplido la misión recién (sello, celebración y, en una escalera, el nivel que
     * le corresponde de una vez). false si no tenía nada para reclamar en esa misión o ya lo había reclamado.
     */
    public boolean reclamar(long userId, long misionId, LocalDateTime ahora) {
        Registro reg = historial.registros(userId).get(misionId);
        if (reg == null || !"PENDIENTE".equals(reg.estado())) return false;
        Mision m = datos.misionesActivas(userId).stream().filter(x -> x.id() == misionId).findFirst().orElse(null);
        if (m == null) m = historial.misionesPorIds(List.of(misionId)).stream().findFirst().orElse(null);
        if (m == null || !HistorialLogica.elegible(m)) return false;

        if ("SAGA".equals(m.familia()) && m.sagaId() != null) {   // la saga pudo sumar una película desde que se ofreció: sin todas votadas no hay sello
            int v = datos.votadasDeSaga(userId, m.sagaId());
            if (v >= 0 && v < m.meta()) return false;
        }

        Integer tiene = datos.nivelesDeSellos(userId).get(m.sello().id());
        if (!historial.marcarReclamado(userId, misionId, ahora)) return false;          // la marca va primero: dos toques seguidos no otorgan dos veces
        if (tiene != null && tiene >= m.nivelSello() && !m.esEscalera()) return true;   // ya tenía ese sello (por otro camino): no hay nada más que dar

        Fila f = datos.filaParaActualizar(userId, m.id(), "P", ahora);
        if ("CUMPLIDA".equals(f.estado())) return true;
        int nuevo = f.progreso() + reg.base();

        if (m.esEscalera()) {
            int nivel = f.nivel();
            while (true) {
                int meta = m.metaDelNivel(nivel);
                if (meta < 0 || nuevo < meta) break;
                nivel++;
                datos.otorgarSello(userId, m.sello().id(), nivel, m.id(), ahora);
            }
            if (nivel > f.nivel()) datos.crearCelebracion(userId, m.id(), nivel, "NIVEL", ahora);      // una sola pantalla, con el nivel final
            datos.actualizarContador(f.id(), nuevo, nivel, f.veces(), ahora);
            if (m.metaDelNivel(nivel) < 0) datos.cumplir(f.id(), nuevo, ahora);
        } else if (nuevo >= m.meta()) {
            if (datos.cumplir(f.id(), m.meta(), ahora)) {
                datos.otorgarSello(userId, m.sello().id(), m.nivelSello(), m.id(), ahora);
                datos.crearCelebracion(userId, m.id(), m.nivelSello(), "NIVEL", ahora);
            }
        } else {
            datos.actualizarProgreso(f.id(), nuevo, ahora);        // no llegó (un título que ya estaba contado): queda el avance, sin sello
        }

        // una rotativa que no le había tocado: se anota como completada, para que no se le vuelva a sortear
        if (m.esRotativa()) {
            boolean asignada = datos.asignaciones(userId).stream().anyMatch(a -> a.misionId() == misionId);
            Long objetivo = asignada ? null : historial.objetivoDeMision(m.id());
            if (objetivo != null) datos.asignar(userId, m.familia(), objetivo, "COMPLETADO", ahora);
        }
        return true;
    }
}