package com.example.demo.application.services;

import com.example.demo.domain.juego.JuegoCampania;
import com.example.demo.domain.juego.JuegoCampaniaRepository;
import com.example.demo.domain.juego.JuegoPregunta;
import com.example.demo.domain.juego.JuegoPreguntaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Plataforma de juegos (admin): campañas, banco de preguntas y tandas diarias.
 *
 * Reglas centrales:
 * - Un día de la campaña queda BLOQUEADO desde que empieza a jugarse (el más
 *   tarde entre las 00:00 de ese día y el inicio de la campaña). Sus preguntas
 *   ya no se editan, mueven ni borran: cambiarían resultados ya contados.
 * - Cada pregunta sale un solo día: está asignada a una fecha o libre.
 * - Para publicar, cada día de la campaña necesita su tanda completa.
 * - ACTIVA y CERRADA las pone el servidor según las fechas (sincronizarEstado).
 */
@Service
public class JuegoAdminService {

    static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final JuegoCampaniaRepository campaniaRepository;
    private final JuegoPreguntaRepository preguntaRepository;
    private Clock clock = Clock.system(ARGENTINA);
    private Random random = new Random();

    public JuegoAdminService(JuegoCampaniaRepository campaniaRepository,
                             JuegoPreguntaRepository preguntaRepository) {
        this.campaniaRepository = campaniaRepository;
        this.preguntaRepository = preguntaRepository;
    }

    // Solo para pruebas.
    void usarRelojYAzar(Clock clock, Random random) {
        this.clock = clock;
        this.random = random;
    }

    // ===================================================================
    // Errores con su código HTTP: el controller los traduce.
    // ===================================================================
    public static class JuegoException extends RuntimeException {
        private final int status;

        public JuegoException(int status, String mensaje) {
            super(mensaje);
            this.status = status;
        }

        public int getStatus() { return status; }
    }

    private static JuegoException invalido(String mensaje) { return new JuegoException(400, mensaje); }
    private static JuegoException noEncontrado(String mensaje) { return new JuegoException(404, mensaje); }
    private static JuegoException conflicto(String mensaje) { return new JuegoException(409, mensaje); }

    // ===================================================================
    // Pedidos y respuestas
    // ===================================================================
    public record CampaniaRequest(String nombre, String slug, String bajada,
                                  LocalDateTime inicio, LocalDateTime cierre,
                                  Integer preguntasPorDia, Integer segundosPorPregunta,
                                  String premioTexto, String premioImagenUrl, Long rewardId,
                                  Integer cantidadSuplentes, Integer diasParaReclamar,
                                  String basesCondiciones, String fondoUrl, String colorAcento,
                                  String premioDescripcion) {}

    public record PreguntaRequest(String texto, String imagenUrl, List<String> opciones,
                                  Integer indiceCorrecta, String origen, String referenciaTmdb) {}

    public record DiaResumen(LocalDate fecha, int asignadas, int requeridas, boolean bloqueado) {}

    public record TandasResumen(List<DiaResumen> dias, int libres, int totalPreguntas,
                                int requeridasEnTotal, int diasCompletos, int diasAlcanzables,
                                int faltan) {}

    public record CampaniaDetalle(JuegoCampania campania, TandasResumen tandas) {}

    public record LoteResultado(int guardadas, List<String> errores) {}

    // ===================================================================
    // Tiempo y estado
    // ===================================================================
    LocalDateTime ahora() { return LocalDateTime.now(clock); }

    // Días de juego de la campaña, del día de inicio al día de cierre inclusive.
    List<LocalDate> dias(JuegoCampania c) {
        List<LocalDate> lista = new ArrayList<>();
        LocalDate d = c.getInicio().toLocalDate();
        LocalDate fin = c.getCierre().toLocalDate();
        while (!d.isAfter(fin)) {
            lista.add(d);
            d = d.plusDays(1);
        }
        return lista;
    }

    // Un día está bloqueado desde que empieza a jugarse.
    boolean diaBloqueado(JuegoCampania c, LocalDate dia) {
        LocalDateTime arranque = dia.atStartOfDay();
        if (c.getInicio().isAfter(arranque)) arranque = c.getInicio();
        return !ahora().isBefore(arranque);
    }

    boolean empezo(JuegoCampania c) {
        return !ahora().isBefore(c.getInicio());
    }

    // Pasa la campaña de PROGRAMADA a ACTIVA, o a CERRADA, según la hora.
    // Devuelve true si cambió.
    @Transactional
    public boolean sincronizarEstado(JuegoCampania c) {
        String antes = c.getEstado();
        LocalDateTime ahora = ahora();
        if (JuegoCampania.PROGRAMADA.equals(c.getEstado()) && !ahora.isBefore(c.getInicio())) {
            c.setEstado(JuegoCampania.ACTIVA);
        }
        if (JuegoCampania.ACTIVA.equals(c.getEstado()) && !ahora.isBefore(c.getCierre())) {
            c.setEstado(JuegoCampania.CERRADA);
        }
        if (!antes.equals(c.getEstado())) {
            campaniaRepository.save(c);
            return true;
        }
        return false;
    }

    // ===================================================================
    // Campañas
    // ===================================================================
    @Transactional
    public List<JuegoCampania> listar() {
        List<JuegoCampania> lista = campaniaRepository.findAllByOrderByInicioDesc();
        lista.forEach(this::sincronizarEstado);
        return lista;
    }

    @Transactional
    public CampaniaDetalle detalle(Long id) {
        JuegoCampania c = buscar(id);
        sincronizarEstado(c);
        return new CampaniaDetalle(c, resumen(c));
    }

    @Transactional
    public JuegoCampania crear(CampaniaRequest req) {
        if (req == null) throw invalido("Faltan los datos de la campaña");
        JuegoCampania c = new JuegoCampania();
        validarEstructura(req);
        validarPresentacion(req);
        exigirInicioFuturo(req.inicio());
        c.setSlug(slugValido(req.slug(), req.nombre(), null));
        aplicarEstructura(c, req);
        aplicarPresentacion(c, req);
        return campaniaRepository.save(c);
    }

    // En BORRADOR o PROGRAMADA sin empezar se puede cambiar todo (el slug, solo
    // en BORRADOR: el enlace ya puede estar compartido). Una vez empezada, solo
    // lo que no altera el juego: textos, premio, bases y estética.
    @Transactional
    public JuegoCampania editar(Long id, CampaniaRequest req) {
        if (req == null) throw invalido("Faltan los datos de la campaña");
        JuegoCampania c = buscar(id);
        sincronizarEstado(c);
        if (JuegoCampania.ARCHIVADA.equals(c.getEstado())) throw conflicto("La campaña está archivada");

        boolean estructuraEditable = !empezo(c)
                && (JuegoCampania.BORRADOR.equals(c.getEstado()) || JuegoCampania.PROGRAMADA.equals(c.getEstado()));

        validarPresentacion(req);
        if (estructuraEditable) {
            validarEstructura(req);
            if (JuegoCampania.BORRADOR.equals(c.getEstado())) {
                c.setSlug(slugValido(req.slug(), req.nombre(), c.getId()));
            } else if (req.slug() != null && !req.slug().isBlank() && !slugNormalizado(req.slug()).equals(c.getSlug())) {
                throw conflicto("La dirección solo se puede cambiar en borrador: el enlace ya puede estar compartido");
            }
            boolean cambia = cambiaEstructura(c, req);
            if (req.inicio() != null && !req.inicio().equals(c.getInicio())) exigirInicioFuturo(req.inicio());
            aplicarEstructura(c, req);
            reacomodarTandas(c);
            // Una campaña programada ya pasó el control de publicar: si el cambio
            // deja días sin su tanda completa, se rechaza (y no se guarda nada).
            if (cambia && JuegoCampania.PROGRAMADA.equals(c.getEstado())) {
                TandasResumen t = resumen(c);
                if (t.diasCompletos() < t.dias().size()) {
                    throw conflicto("Con estas fechas quedarían días sin preguntas (" + t.diasCompletos() + " de "
                            + t.dias().size() + " días completos). Pasá la campaña a borrador, armá las tandas y volvé a publicar.");
                }
            }
        } else if (cambiaEstructura(c, req)) {
            throw conflicto("La campaña ya empezó: no se pueden cambiar las fechas, las preguntas por día ni el tiempo por pregunta");
        }
        aplicarPresentacion(c, req);
        return campaniaRepository.save(c);
    }

    @Transactional
    public void eliminar(Long id) {
        JuegoCampania c = buscar(id);
        if (!JuegoCampania.BORRADOR.equals(c.getEstado())) {
            throw conflicto("Solo se puede borrar una campaña en borrador");
        }
        campaniaRepository.delete(c); // las preguntas se borran en cascada (ON DELETE CASCADE)
    }

    // BORRADOR → PROGRAMADA, si la campaña está completa. El error lista todo lo que falta.
    @Transactional
    public JuegoCampania publicar(Long id) {
        JuegoCampania c = buscar(id);
        if (!JuegoCampania.BORRADOR.equals(c.getEstado())) throw conflicto("Solo se publica una campaña en borrador");

        List<String> faltan = new ArrayList<>();
        if (vacio(c.getBasesCondiciones())) faltan.add("las bases y condiciones");
        if (vacio(c.getPremioTexto())) faltan.add("el premio");
        if (!c.getCierre().isAfter(ahora())) faltan.add("una fecha de cierre futura");
        TandasResumen t = resumen(c);
        if (t.diasCompletos() < t.dias().size()) {
            faltan.add("completar las tandas (" + t.diasCompletos() + " de " + t.dias().size() + " días completos)");
        }
        if (!faltan.isEmpty()) throw conflicto("Para publicar falta: " + String.join(", ", faltan));

        c.setEstado(JuegoCampania.PROGRAMADA);
        campaniaRepository.save(c);
        sincronizarEstado(c); // si el inicio ya pasó, queda ACTIVA en el momento
        return c;
    }

    @Transactional
    public JuegoCampania volverABorrador(Long id) {
        JuegoCampania c = buscar(id);
        sincronizarEstado(c);
        if (!JuegoCampania.PROGRAMADA.equals(c.getEstado())) {
            throw conflicto("Solo vuelve a borrador una campaña programada que todavía no empezó");
        }
        c.setEstado(JuegoCampania.BORRADOR);
        return campaniaRepository.save(c);
    }

    @Transactional
    public JuegoCampania archivar(Long id) {
        JuegoCampania c = buscar(id);
        if (!JuegoCampania.PUBLICADA.equals(c.getEstado())) {
            throw conflicto("Solo se archiva una campaña con ganadores publicados");
        }
        c.setEstado(JuegoCampania.ARCHIVADA);
        return campaniaRepository.save(c);
    }

    // Antes de generar desde TMDb: que la campaña exista y su banco se pueda tocar.
    @Transactional
    public void buscarParaGenerar(Long id) {
        JuegoCampania c = buscar(id);
        sincronizarEstado(c);
        exigirBancoEditable(c);
    }

    JuegoCampania buscar(Long id) {
        return campaniaRepository.findById(id).orElseThrow(() -> noEncontrado("No existe la campaña"));
    }

    // ===================================================================
    // Banco de preguntas
    // ===================================================================
    @Transactional(readOnly = true)
    public List<JuegoPregunta> preguntas(Long campaniaId) {
        buscar(campaniaId);
        return preguntaRepository.findByCampaniaIdOrderByDiaAsignadoAscOrdenEnDiaAscIdAsc(campaniaId);
    }

    @Transactional
    public JuegoPregunta crearPregunta(Long campaniaId, PreguntaRequest req) {
        JuegoCampania c = buscar(campaniaId);
        sincronizarEstado(c);
        exigirBancoEditable(c);
        JuegoPregunta p = new JuegoPregunta();
        p.setCampaniaId(c.getId());
        aplicarPregunta(p, req);
        return preguntaRepository.save(p);
    }

    // Varias preguntas de una vez (las elegidas de las generadas desde TMDb).
    // Guarda las válidas y devuelve el motivo de cada una que no entró.
    @Transactional
    public LoteResultado crearPreguntasLote(Long campaniaId, List<PreguntaRequest> lote) {
        JuegoCampania c = buscar(campaniaId);
        sincronizarEstado(c);
        exigirBancoEditable(c);
        if (lote == null || lote.isEmpty()) throw invalido("No elegiste ninguna pregunta");
        if (lote.size() > 100) throw invalido("Se pueden agregar hasta 100 preguntas por vez");

        Set<String> referencias = new HashSet<>();
        for (JuegoPregunta p : preguntaRepository.findByCampaniaIdOrderByDiaAsignadoAscOrdenEnDiaAscIdAsc(c.getId())) {
            if (p.getReferenciaTmdb() != null) referencias.add(p.getReferenciaTmdb());
        }
        List<JuegoPregunta> nuevas = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        for (int i = 0; i < lote.size(); i++) {
            PreguntaRequest req = lote.get(i);
            try {
                String ref = req == null ? null : limpio(req.referenciaTmdb());
                if (ref != null && !referencias.add(ref)) throw invalido("ya está en el banco");
                JuegoPregunta p = new JuegoPregunta();
                p.setCampaniaId(c.getId());
                aplicarPregunta(p, req);
                nuevas.add(p);
            } catch (JuegoException e) {
                errores.add("Pregunta " + (i + 1) + ": " + e.getMessage());
            }
        }
        preguntaRepository.saveAll(nuevas);
        return new LoteResultado(nuevas.size(), errores);
    }

    @Transactional
    public JuegoPregunta editarPregunta(Long preguntaId, PreguntaRequest req) {
        JuegoPregunta p = buscarPregunta(preguntaId);
        JuegoCampania c = buscar(p.getCampaniaId());
        exigirBancoEditable(c);
        exigirPreguntaNoJugada(c, p);
        aplicarPregunta(p, req);
        return preguntaRepository.save(p);
    }

    @Transactional
    public void eliminarPregunta(Long preguntaId) {
        JuegoPregunta p = buscarPregunta(preguntaId);
        JuegoCampania c = buscar(p.getCampaniaId());
        exigirBancoEditable(c);
        exigirPreguntaNoJugada(c, p);
        LocalDate dia = p.getDiaAsignado();
        preguntaRepository.delete(p);
        if (dia != null) renumerar(c.getId(), dia);
    }

    private JuegoPregunta buscarPregunta(Long id) {
        return preguntaRepository.findById(id).orElseThrow(() -> noEncontrado("No existe la pregunta"));
    }

    private void exigirBancoEditable(JuegoCampania c) {
        String e = c.getEstado();
        if (JuegoCampania.CERRADA.equals(e) || JuegoCampania.PUBLICADA.equals(e) || JuegoCampania.ARCHIVADA.equals(e)) {
            throw conflicto("La campaña ya cerró: el banco de preguntas no se puede modificar");
        }
    }

    private void exigirPreguntaNoJugada(JuegoCampania c, JuegoPregunta p) {
        if (p.getDiaAsignado() != null && diaBloqueado(c, p.getDiaAsignado())) {
            throw conflicto("Esa pregunta ya se jugó (o se está jugando): no se puede cambiar");
        }
    }

    private void aplicarPregunta(JuegoPregunta p, PreguntaRequest req) {
        if (req == null) throw invalido("Faltan los datos de la pregunta");
        String texto = limpio(req.texto());
        if (texto == null) throw invalido("Falta el texto de la pregunta");
        if (texto.length() > 400) throw invalido("La pregunta es demasiado larga (máximo 400 caracteres)");

        List<String> opciones = new ArrayList<>();
        if (req.opciones() != null) {
            for (String o : req.opciones()) {
                String op = limpio(o);
                if (op != null) opciones.add(op);
            }
        }
        if (opciones.size() < 2 || opciones.size() > 4) throw invalido("Cada pregunta lleva de 2 a 4 opciones");
        Set<String> vistas = new HashSet<>();
        for (String op : opciones) {
            if (op.length() > 200) throw invalido("Hay una opción demasiado larga (máximo 200 caracteres)");
            if (!vistas.add(op.toLowerCase(Locale.ROOT))) throw invalido("Hay opciones repetidas");
        }
        Integer correcta = req.indiceCorrecta();
        if (correcta == null || correcta < 1 || correcta > opciones.size()) {
            throw invalido("Indicá cuál es la opción correcta");
        }

        p.setTexto(texto);
        p.setImagenUrl(urlValida(req.imagenUrl(), "La imagen"));
        p.setOpcion1(opciones.get(0));
        p.setOpcion2(opciones.get(1));
        p.setOpcion3(opciones.size() > 2 ? opciones.get(2) : null);
        p.setOpcion4(opciones.size() > 3 ? opciones.get(3) : null);
        p.setIndiceCorrecta(correcta);
        p.setOrigen(JuegoPregunta.ORIGEN_TMDB.equals(req.origen()) ? JuegoPregunta.ORIGEN_TMDB : JuegoPregunta.ORIGEN_MANUAL);
        String ref = limpio(req.referenciaTmdb());
        p.setReferenciaTmdb(ref != null && ref.length() > 100 ? ref.substring(0, 100) : ref);
    }

    // ===================================================================
    // Tandas diarias
    // ===================================================================
    @Transactional(readOnly = true)
    public TandasResumen resumen(JuegoCampania c) {
        List<JuegoPregunta> banco = preguntaRepository.findByCampaniaIdOrderByDiaAsignadoAscOrdenEnDiaAscIdAsc(c.getId());
        Map<LocalDate, Integer> porDia = new HashMap<>();
        int libres = 0;
        for (JuegoPregunta p : banco) {
            if (p.getDiaAsignado() == null) libres++;
            else porDia.merge(p.getDiaAsignado(), 1, Integer::sum);
        }
        int req = c.getPreguntasPorDia();
        List<DiaResumen> dias = new ArrayList<>();
        int completos = 0, huecos = 0;
        for (LocalDate d : dias(c)) {
            int asignadas = porDia.getOrDefault(d, 0);
            boolean bloqueado = diaBloqueado(c, d);
            dias.add(new DiaResumen(d, asignadas, req, bloqueado));
            if (asignadas >= req) completos++;
            else if (!bloqueado) huecos += req - asignadas;
        }
        // Cuántos días quedarían completos si se repartieran las libres, en orden.
        int alcanzables = completos, disponibles = libres;
        for (DiaResumen d : dias) {
            if (d.asignadas() >= req || d.bloqueado()) continue;
            int falta = req - d.asignadas();
            if (disponibles < falta) break;
            disponibles -= falta;
            alcanzables++;
        }
        int faltan = Math.max(0, huecos - libres);
        return new TandasResumen(dias, libres, banco.size(), req * dias.size(), completos, alcanzables, faltan);
    }

    // Completa los días no bloqueados con preguntas libres elegidas al azar.
    @Transactional
    public TandasResumen asignarAutomatico(Long campaniaId) {
        JuegoCampania c = buscar(campaniaId);
        sincronizarEstado(c);
        exigirBancoEditable(c);

        List<JuegoPregunta> banco = preguntaRepository.findByCampaniaIdOrderByDiaAsignadoAscOrdenEnDiaAscIdAsc(c.getId());
        List<JuegoPregunta> libres = new ArrayList<>(banco.stream().filter(p -> p.getDiaAsignado() == null).toList());
        Collections.shuffle(libres, random);

        Map<LocalDate, Integer> porDia = new HashMap<>();
        for (JuegoPregunta p : banco) {
            if (p.getDiaAsignado() != null) porDia.merge(p.getDiaAsignado(), 1, Integer::sum);
        }
        Iterator<JuegoPregunta> it = libres.iterator();
        List<JuegoPregunta> cambiadas = new ArrayList<>();
        for (LocalDate d : dias(c)) {
            if (diaBloqueado(c, d)) continue;
            int asignadas = porDia.getOrDefault(d, 0);
            while (asignadas < c.getPreguntasPorDia() && it.hasNext()) {
                JuegoPregunta p = it.next();
                asignadas++;
                p.setDiaAsignado(d);
                p.setOrdenEnDia(asignadas);
                cambiadas.add(p);
            }
            if (!it.hasNext()) break;
        }
        preguntaRepository.saveAll(cambiadas);
        return resumen(c);
    }

    // Mueve una pregunta a otro día (al final de su tanda) o la libera (dia = null).
    @Transactional
    public TandasResumen moverPregunta(Long preguntaId, LocalDate dia) {
        JuegoPregunta p = buscarPregunta(preguntaId);
        JuegoCampania c = buscar(p.getCampaniaId());
        sincronizarEstado(c);
        exigirBancoEditable(c);
        exigirPreguntaNoJugada(c, p);

        LocalDate origen = p.getDiaAsignado();
        if (Objects.equals(origen, dia)) return resumen(c);

        if (dia != null) {
            if (!dias(c).contains(dia)) throw invalido("Ese día no está dentro de la campaña");
            if (diaBloqueado(c, dia)) throw conflicto("Ese día ya empezó: no se le pueden agregar preguntas");
            int asignadas = preguntaRepository.findByCampaniaIdAndDiaAsignadoOrderByOrdenEnDiaAsc(c.getId(), dia).size();
            if (asignadas >= c.getPreguntasPorDia()) throw conflicto("Ese día ya tiene su tanda completa");
            p.setDiaAsignado(dia);
            p.setOrdenEnDia(asignadas + 1);
        } else {
            p.setDiaAsignado(null);
            p.setOrdenEnDia(null);
        }
        preguntaRepository.save(p);
        if (origen != null) renumerar(c.getId(), origen);
        return resumen(c);
    }

    // Deja el orden de una tanda en 1, 2, 3... sin huecos.
    private void renumerar(Long campaniaId, LocalDate dia) {
        List<JuegoPregunta> tanda = preguntaRepository.findByCampaniaIdAndDiaAsignadoOrderByOrdenEnDiaAsc(campaniaId, dia);
        int orden = 1;
        for (JuegoPregunta p : tanda) p.setOrdenEnDia(orden++);
        preguntaRepository.saveAll(tanda);
    }

    // Después de cambiar fechas o preguntas por día: libera las preguntas de
    // días que ya no existen y las que sobran en cada tanda.
    private void reacomodarTandas(JuegoCampania c) {
        Set<LocalDate> validos = new HashSet<>(dias(c));
        List<JuegoPregunta> banco = preguntaRepository.findByCampaniaIdOrderByDiaAsignadoAscOrdenEnDiaAscIdAsc(c.getId());
        List<JuegoPregunta> cambiadas = new ArrayList<>();
        for (JuegoPregunta p : banco) {
            if (p.getDiaAsignado() == null) continue;
            boolean fuera = !validos.contains(p.getDiaAsignado());
            boolean sobra = p.getOrdenEnDia() != null && p.getOrdenEnDia() > c.getPreguntasPorDia();
            if (fuera || sobra) {
                p.setDiaAsignado(null);
                p.setOrdenEnDia(null);
                cambiadas.add(p);
            }
        }
        if (!cambiadas.isEmpty()) preguntaRepository.saveAll(cambiadas);
    }

    // ===================================================================
    // Validaciones y aplicación de campos
    // ===================================================================
    private void validarEstructura(CampaniaRequest req) {
        if (req.inicio() == null || req.cierre() == null) throw invalido("Faltan las fechas de inicio y cierre");
        if (!req.cierre().isAfter(req.inicio())) throw invalido("El cierre tiene que ser posterior al inicio");
        long dias = req.cierre().toLocalDate().toEpochDay() - req.inicio().toLocalDate().toEpochDay() + 1;
        if (dias > 90) throw invalido("Una campaña dura como máximo 90 días");
        entre(req.preguntasPorDia(), 1, 30, "Las preguntas por día");
        entre(req.segundosPorPregunta(), 5, 120, "El tiempo por pregunta (en segundos)");
    }

    // Si el inicio ya pasó, el primer día arranca bloqueado y sin preguntas.
    private void exigirInicioFuturo(LocalDateTime inicio) {
        if (inicio != null && !inicio.isAfter(ahora())) {
            throw invalido("El inicio tiene que ser una fecha y hora futura (hora de Argentina)");
        }
    }

    private void validarPresentacion(CampaniaRequest req) {
        String nombre = limpio(req.nombre());
        if (nombre == null) throw invalido("Falta el nombre de la campaña");
        if (nombre.length() > 120) throw invalido("El nombre es demasiado largo (máximo 120 caracteres)");
        largo(req.bajada(), 300, "La bajada");
        largo(req.premioTexto(), 300, "El premio");
        largo(req.premioDescripcion(), 3000, "La descripción del premio");
        urlValida(req.premioImagenUrl(), "La imagen del premio");
        urlValida(req.fondoUrl(), "El fondo");
        if (req.cantidadSuplentes() != null) entre(req.cantidadSuplentes(), 0, 5, "La cantidad de suplentes");
        if (req.diasParaReclamar() != null) entre(req.diasParaReclamar(), 1, 60, "Los días para reclamar");
        String color = limpio(req.colorAcento());
        if (color != null && !color.matches("#[0-9a-fA-F]{6}")) throw invalido("El color tiene que tener el formato #RRGGBB");
    }

    private void aplicarEstructura(JuegoCampania c, CampaniaRequest req) {
        c.setInicio(req.inicio());
        c.setCierre(req.cierre());
        c.setPreguntasPorDia(req.preguntasPorDia());
        c.setSegundosPorPregunta(req.segundosPorPregunta());
    }

    private void aplicarPresentacion(JuegoCampania c, CampaniaRequest req) {
        c.setNombre(limpio(req.nombre()));
        c.setBajada(limpio(req.bajada()));
        c.setPremioTexto(limpio(req.premioTexto()));
        c.setPremioImagenUrl(urlValida(req.premioImagenUrl(), "La imagen del premio"));
        c.setRewardId(req.rewardId());
        if (req.cantidadSuplentes() != null) c.setCantidadSuplentes(req.cantidadSuplentes());
        if (req.diasParaReclamar() != null) c.setDiasParaReclamar(req.diasParaReclamar());
        c.setBasesCondiciones(limpio(req.basesCondiciones()));
        c.setPremioDescripcion(limpio(req.premioDescripcion()));
        c.setFondoUrl(urlValida(req.fondoUrl(), "El fondo"));
        String color = limpio(req.colorAcento());
        c.setColorAcento(color == null ? null : color.toLowerCase(Locale.ROOT));
    }

    private boolean cambiaEstructura(JuegoCampania c, CampaniaRequest req) {
        return (req.inicio() != null && !req.inicio().equals(c.getInicio()))
                || (req.cierre() != null && !req.cierre().equals(c.getCierre()))
                || (req.preguntasPorDia() != null && !req.preguntasPorDia().equals(c.getPreguntasPorDia()))
                || (req.segundosPorPregunta() != null && !req.segundosPorPregunta().equals(c.getSegundosPorPregunta()));
    }

    // Dirección de la campaña: la pedida o, si no viene, la derivada del nombre.
    private String slugValido(String pedido, String nombre, Long idActual) {
        String base = slugNormalizado(pedido != null && !pedido.isBlank() ? pedido : nombre);
        if (base.isEmpty()) throw invalido("La dirección no puede quedar vacía");
        if (base.length() > 80) base = base.substring(0, 80).replaceAll("-+$", "");
        boolean ocupado = idActual == null
                ? campaniaRepository.existsBySlug(base)
                : campaniaRepository.existsBySlugAndIdNot(base, idActual);
        if (ocupado) throw conflicto("Ya hay una campaña con la dirección \"" + base + "\"");
        return base;
    }

    static String slugNormalizado(String texto) {
        if (texto == null) return "";
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static void entre(Integer valor, int min, int max, String que) {
        if (valor == null || valor < min || valor > max) throw invalido(que + " tiene que estar entre " + min + " y " + max);
    }

    private static void largo(String texto, int max, String que) {
        String t = limpio(texto);
        if (t != null && t.length() > max) throw invalido(que + " es demasiado largo (máximo " + max + " caracteres)");
    }

    private static String urlValida(String url, String que) {
        String u = limpio(url);
        if (u == null) return null;
        if (u.length() > 500) throw invalido(que + ": el enlace es demasiado largo");
        if (!(u.startsWith("https://") || u.startsWith("http://"))) throw invalido(que + ": el enlace tiene que empezar con https://");
        return u;
    }

    private static String limpio(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean vacio(String texto) {
        return limpio(texto) == null;
    }
}