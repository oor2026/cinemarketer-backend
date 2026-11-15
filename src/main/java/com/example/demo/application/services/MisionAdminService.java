package com.example.demo.application.services;

import com.example.demo.domain.mision.*;
import com.example.demo.domain.mision.MisionAdminDatos.*;
import com.example.demo.domain.mision.MisionAdminReglas.Metrica;
import com.example.demo.domain.mision.MisionAdminValidador.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Lo que el panel de admin puede hacer con las misiones y los sellos: crear, editar, publicar, pausar, duplicar y borrar, con todas las validaciones. Cada cambio queda
 * en el historial. Las misiones AUTOMÁTICAS (la rotación) no se tocan desde acá: se administran desde el catálogo.
 * Los errores salen como {@link AdminException} con su código HTTP y la lista de problemas por campo.
 */
@Service
public class MisionAdminService {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    /** Lo que se bloquea cuando alguien ya tiene avance en la misión: cambiarlo cambiaría el sentido de lo que ya hicieron. */
    static final List<String> CAMPOS_BLOQUEADOS = List.of("metrica", "tipoContenido", "generoId", "directorId", "sagaId", "actorId", "distintas", "vigencia", "escalera", "escaleraSobre", "selloExistenteId");

    public static class AdminException extends RuntimeException {
        private final int http;
        private final List<Problema> errores, avisos;

        public AdminException(int http, String mensaje, List<Problema> errores, List<Problema> avisos) { super(mensaje); this.http = http; this.errores = errores; this.avisos = avisos; }
        public AdminException(int http, String mensaje) { this(http, mensaje, List.of(), List.of()); }
        public int http() { return http; }
        public List<Problema> errores() { return errores; }
        public List<Problema> avisos() { return avisos; }
    }

    private final MisionAdminDatos datos;
    private final CatalogoTmdb tmdb;
    private final Supplier<LocalDateTime> reloj;

    @Autowired
    public MisionAdminService(MisionAdminDatos datos, CatalogoTmdb tmdb) { this(datos, tmdb, () -> LocalDateTime.now(AR)); }

    /** Para las pruebas: con un reloj fijo. */
    MisionAdminService(MisionAdminDatos datos, CatalogoTmdb tmdb, Supplier<LocalDateTime> reloj) { this.datos = datos; this.tmdb = tmdb; this.reloj = reloj; }

    // ================================================================================================ lo que el asistente necesita para armarse
    /** Las reglas, tal cual las usa el validador: el asistente ofrece solo lo que tiene sentido para cada tipo de misión. */
    public Map<String, Object> opciones() {
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> metricas = new ArrayList<>();
        for (Metrica m : MisionAdminReglas.METRICAS) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("codigo", m.codigo()); x.put("etiqueta", m.etiqueta()); x.put("ayuda", m.ayuda()); x.put("unidad", m.unidad()); x.put("tipoContenido", m.tipoContenido());
            x.put("filtrosPelicula", m.filtrosPelicula()); x.put("distintas", m.distintas()); x.put("vigencias", m.vigencias()); x.put("escalera", m.escalera()); x.put("metaMax", m.metaMax());
            metricas.add(x);
        }
        r.put("metricas", metricas);
        r.put("categorias", MisionAdminReglas.CATEGORIAS);
        r.put("vigencias", MisionAdminReglas.VIGENCIAS);
        r.put("visibilidades", MisionAdminReglas.VISIBILIDADES);
        r.put("audiencias", MisionAdminReglas.AUDIENCIAS);
        List<Map<String, Object>> dificultades = new ArrayList<>();
        for (String d : MisionAdminReglas.DIFICULTADES) dificultades.add(Map.of("codigo", d, "esfuerzo", MisionAdminReglas.esfuerzo(d)));
        r.put("dificultades", dificultades);
        List<Map<String, Object>> destinos = new ArrayList<>();
        MisionAdminReglas.DESTINOS.forEach((k, v) -> destinos.add(Map.of("codigo", k, "etiqueta", v)));
        r.put("destinos", destinos);
        r.put("imagen", Map.of("maxKb", ValidadorImagenSello.MAX_BYTES / 1024, "minLado", ValidadorImagenSello.MIN_LADO, "maxLado", ValidadorImagenSello.MAX_LADO, "formatos", List.of("PNG", "WebP", "JPG")));
        r.put("estados", List.of("BORRADOR", "ACTIVA", "PAUSADA", "PROGRAMADA", "VENCIDA"));
        r.put("camposBloqueables", CAMPOS_BLOQUEADOS);
        return r;
    }

    // ================================================================================================ lectura
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Filtros filtros) {
        LocalDateTime ahora = reloj.get();
        List<Map<String, Object>> r = new ArrayList<>();
        for (Fila f : datos.listar(filtros, MisionLogica.ciclo("HOY", ahora.toLocalDate()), MisionLogica.ciclo("SEMANA", ahora.toLocalDate()), ahora)) r.add(item(f, ahora));
        return r;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(long id) {
        LocalDateTime ahora = reloj.get();
        return detalle(obtener(id, ahora), ahora);
    }

    @Transactional(readOnly = true)
    public List<SelloItem> sellos() { return datos.sellos(); }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historial(long id) {
        obtener(id, reloj.get());
        List<Map<String, Object>> r = new ArrayList<>();
        for (HistorialItem h : datos.historial(id, 100)) r.add(historialMapa(h));
        return r;
    }

    /** Solo valida (no guarda): lo usa el asistente para mostrar errores y avisos mientras el admin completa los pasos. */
    @Transactional(readOnly = true)
    public Map<String, Object> validar(MisionAdminForm f, Long misionId) {
        LocalDateTime ahora = reloj.get();
        Long selloActual = null;
        Fila actual = null;
        if (misionId != null) { actual = obtener(misionId, ahora); selloActual = actual.selloId(); }
        Resultado r = MisionAdminValidador.validar(f, misionId, selloActual, entorno());
        List<Problema> errores = new ArrayList<>(r.errores()), avisos = new ArrayList<>(r.avisos());
        if (r.ok() && actual != null && actual.conProgreso()) {
            errores.addAll(bloqueos(actual, r.normalizada()));
            avisos.addAll(avisosDeProgreso(actual, r.normalizada()));
        }
        return resultadoMapa(errores, avisos);
    }

    // ================================================================================================ escritura
    @Transactional
    public Map<String, Object> crear(MisionAdminForm f, long adminId) {
        LocalDateTime ahora = reloj.get();
        datos.serializarCreaciones();                                       // dos admins no pueden crear a la vez el mismo nombre
        Resultado r = MisionAdminValidador.validar(f, null, null, entorno());
        if (!r.ok()) throw new AdminException(422, "Revisá los datos de la misión.", r.errores(), r.avisos());
        Normalizada n = r.normalizada();
        long id = datos.crear(n, adminId, ahora);
        datos.registrarHistorial(id, n.titulo(), adminId, "CREAR", "Creada como borrador (" + n.metrica() + ", meta " + n.meta() + ", " + n.vigencia() + ", " + n.audiencia() + ")");
        Map<String, Object> d = detalle(obtener(id, ahora), ahora);
        d.put("avisos", problemas(r.avisos()));
        return d;
    }

    @Transactional
    public Map<String, Object> editar(long id, MisionAdminForm f, long adminId) {
        LocalDateTime ahora = reloj.get();
        Fila actual = obtener(id, ahora);
        soloManual(actual, "editarla");
        datos.serializarCreaciones();
        Resultado r = MisionAdminValidador.validar(f, id, actual.selloId(), entorno());
        List<Problema> errores = new ArrayList<>(r.errores());
        if (r.ok() && actual.conProgreso()) errores.addAll(bloqueos(actual, r.normalizada()));
        if (!errores.isEmpty()) throw new AdminException(422, "Revisá los datos de la misión.", errores, r.avisos());
        Normalizada n = conEsfuerzoOriginal(r.normalizada(), actual);
        List<Problema> avisos = new ArrayList<>(r.avisos());
        if (actual.conProgreso()) avisos.addAll(avisosDeProgreso(actual, n));
        boolean retocar = !n.selloNuevo() && f.selloExistenteId() == null;                      // sin elegir otro sello: se conserva y se puede retocar
        String cambios = cambios(actual, n, retocar);
        datos.actualizar(id, n, retocar, adminId, ahora);
        datos.registrarHistorial(id, n.titulo(), adminId, "EDITAR", cambios.isEmpty() ? "Sin cambios de contenido" : cambios);
        Map<String, Object> d = detalle(obtener(id, ahora), ahora);
        d.put("avisos", problemas(avisos));
        return d;
    }

    @Transactional
    public Map<String, Object> publicar(long id, long adminId) {
        LocalDateTime ahora = reloj.get();
        Fila f = obtener(id, ahora);
        soloManual(f, "publicarla");
        if (f.activa()) return detalle(f, ahora);
        Resultado r = MisionAdminValidador.validar(aFormulario(f), id, f.selloId(), entorno());     // se vuelve a revisar: pudo cambiar el mundo desde que se guardó
        if (!r.ok()) throw new AdminException(422, "No se puede publicar todavía: revisá los datos.", r.errores(), r.avisos());
        datos.cambiarActiva(id, true, adminId, ahora);
        datos.registrarHistorial(id, f.titulo(), adminId, "PUBLICAR", f.publicadaAt() == null ? "Primera publicación" : "Publicada de nuevo");
        Map<String, Object> d = detalle(obtener(id, ahora), ahora);
        d.put("avisos", problemas(r.avisos()));
        return d;
    }

    @Transactional
    public Map<String, Object> pausar(long id, long adminId) {
        LocalDateTime ahora = reloj.get();
        Fila f = obtener(id, ahora);
        soloManual(f, "pausarla");
        if (!f.activa()) return detalle(f, ahora);
        datos.cambiarActiva(id, false, adminId, ahora);
        datos.registrarHistorial(id, f.titulo(), adminId, "PAUSAR", f.usuariosEnCurso() > 0 ? f.usuariosEnCurso() + " usuario(s) la tenían en curso: dejan de verla, no pierden su avance" : "Pausada");
        return detalle(obtener(id, ahora), ahora);
    }

    /** Una copia en BORRADOR, con su propio sello (para poder cambiarlo sin tocar el original). Sirve también para hacer una versión nueva de una misión bloqueada. */
    @Transactional
    public Map<String, Object> duplicar(long id, long adminId) {
        LocalDateTime ahora = reloj.get();
        Fila f = obtener(id, ahora);
        datos.serializarCreaciones();
        String titulo = libre(f.titulo(), 100, t -> datos.tituloExiste(t, null)), sello = libre(f.selloNombre(), 80, t -> datos.selloNombreExiste(t, null));
        Normalizada n = new Normalizada(titulo, f.descripcion(), f.descripcionCumplida(), f.categoria(), f.metrica(), f.meta(), f.unidad(), f.vigencia(), f.tipoContenido(), f.generoId(), f.directorId(), f.sagaId(), f.actorId(),
                f.distintas(), true, null, f.nivelSello(), sello, f.selloDescripcion(), f.selloImagenUrl(), f.selloCategoria(), f.esfuerzo(), f.prerrequisitoId(), f.sellosNecesarios(), f.visibilidad(), f.desde(), f.hasta(),
                f.accionTexto(), f.accionDestino(), f.orden(), f.escalera(), f.escaleraPaso(), f.escaleraSobre() == null ? "PROGRESO" : f.escaleraSobre(), f.audiencia());
        long nuevo = datos.crear(n, adminId, ahora);
        datos.registrarHistorial(nuevo, titulo, adminId, "DUPLICAR", "Copia de «" + f.titulo() + "» (#" + f.id() + ")");
        return detalle(obtener(nuevo, ahora), ahora);
    }

    @Transactional
    public void eliminar(long id, long adminId) {
        Fila f = obtener(id, reloj.get());
        soloManual(f, "borrarla");
        if (f.conProgreso()) throw new AdminException(409, "Hay usuarios con avance o con este sello: no se puede borrar. Pausala: deja de mostrarse y nadie pierde nada.");
        List<String> dep = datos.dependientes(id);
        if (!dep.isEmpty()) throw new AdminException(409, "Estas misiones la usan como requisito: " + String.join(", ", dep) + ". Cambiales el requisito antes de borrarla.");
        if (!datos.eliminar(id)) throw new AdminException(409, "No se pudo borrar: alguien acaba de avanzar en ella. Pausala en vez de borrarla.");
        datos.registrarHistorial(id, f.titulo(), adminId, "ELIMINAR", "Borrada (sin usuarios con avance)");
    }

    // ================================================================================================ catálogo automático
    @Transactional(readOnly = true)
    public Map<String, Object> catalogo(String familia, String q, String estado) {
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> resumen = new ArrayList<>();
        for (ResumenFamilia x : datos.resumenCatalogo()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("familia", x.familia()); m.put("total", x.total()); m.put("excluidos", x.excluidos()); m.put("ultimaActualizacion", x.ultimaActualizacion());
            resumen.add(m);
        }
        r.put("resumen", resumen);
        List<Map<String, Object>> objetivos = new ArrayList<>();
        for (ObjetivoFila o : datos.objetivos(familia, q, estado)) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", o.id()); x.put("familia", o.familia()); x.put("tmdbId", o.tmdbId()); x.put("nombre", o.nombre()); x.put("meta", o.meta()); x.put("excluido", o.excluido());
            x.put("retirado", o.excluido() && !o.misionActiva()); x.put("misionId", o.misionId()); x.put("asignados", o.asignados()); x.put("completados", o.completados()); x.put("descartados", o.descartados());
            x.put("actualizadoAt", o.actualizadoAt());
            objetivos.add(x);
        }
        r.put("objetivos", objetivos);
        return r;
    }

    @Transactional
    public void cambiarExclusion(long objetivoId, boolean excluido, boolean retirar, long adminId) {
        if (!datos.cambiarExclusion(objetivoId, excluido, retirar && excluido, adminId, reloj.get())) throw new AdminException(404, "Ese objetivo no existe.");
    }

    // ================================================================================================ piezas
    private Fila obtener(long id, LocalDateTime ahora) {
        return datos.obtener(id, MisionLogica.ciclo("HOY", ahora.toLocalDate()), MisionLogica.ciclo("SEMANA", ahora.toLocalDate())).orElseThrow(() -> new AdminException(404, "Esa misión no existe."));
    }

    private static void soloManual(Fila f, String accion) {
        if ("AUTO".equals(f.origen())) throw new AdminException(409, "Esta misión es parte de la rotación automática: no se puede " + accion + " desde acá. Se administra desde «Catálogo automático».");
    }

    private Entorno entorno() {
        return new Entorno() {
            public boolean tituloExiste(String t, Long excluir) { return datos.tituloExiste(t, excluir); }
            public boolean selloNombreExiste(String n, Long excluir) { return datos.selloNombreExiste(n, excluir); }
            public Optional<SelloRef> sello(long id) { return datos.sello(id); }
            public Optional<MisionRef> mision(long id) { return datos.mision(id); }
            public Double esfuerzoMinimoActivas(Long excluir, String audiencia) { return datos.esfuerzoMinimoActivas(excluir, audiencia); }
            public Integer estrenadasSaga(long id) { try { CatalogoTmdb.Saga s = tmdb.saga(id); return s == null ? null : s.estrenadas(); } catch (Exception e) { return null; } }
            public Integer peliculasActor(long id) { try { return tmdb.peliculasComoActor(id); } catch (Exception e) { return null; } }
            public Integer peliculasDirector(long id) { try { return tmdb.peliculasComoDirector(id); } catch (Exception e) { return null; } }
            public LocalDateTime ahora() { return reloj.get(); }
        };
    }

    /** Si alguien ya tiene avance, lo estructural no se puede cambiar. */
    private static List<Problema> bloqueos(Fila a, Normalizada n) {
        List<Problema> r = new ArrayList<>();
        String msg = "Ya hay usuarios con avance en esta misión: no se puede cambiar %s. Si necesitás otra versión, duplicala.";
        if (!a.metrica().equals(n.metrica())) r.add(new Problema("metrica", String.format(msg, "qué se le pide")));
        if (!Objects.equals(a.tipoContenido(), n.tipoContenido())) r.add(new Problema("tipoContenido", String.format(msg, "película o serie")));
        if (!Objects.equals(a.generoId(), n.generoId())) r.add(new Problema("generoId", String.format(msg, "el género")));
        if (!Objects.equals(a.directorId(), n.directorId())) r.add(new Problema("directorId", String.format(msg, "el director")));
        if (!Objects.equals(a.sagaId(), n.sagaId())) r.add(new Problema("sagaId", String.format(msg, "la saga")));
        if (!Objects.equals(a.actorId(), n.actorId())) r.add(new Problema("actorId", String.format(msg, "el actor o actriz")));
        if (a.distintas() != n.distintas()) r.add(new Problema("distintas", String.format(msg, "«contar distintos»")));
        if (!a.vigencia().equals(n.vigencia())) r.add(new Problema("vigencia", String.format(msg, "la vigencia")));
        if (!Objects.equals(sinEspacios(a.escalera()), sinEspacios(n.escaleraCsv()))) r.add(new Problema("escalera", String.format(msg, "los niveles")));
        if (!Objects.equals(a.escaleraSobre() == null ? "PROGRESO" : a.escaleraSobre(), n.escaleraSobre())) r.add(new Problema("escaleraSobre", String.format(msg, "el tipo de niveles")));
        if (n.selloId() != null && n.selloId() != a.selloId()) r.add(new Problema("selloExistenteId", String.format(msg, "el sello")));
        return r;
    }

    private static List<Problema> avisosDeProgreso(Fila a, Normalizada n) {
        List<Problema> r = new ArrayList<>();
        int hay = a.usuariosEnCurso() + a.usuariosCumplieron();
        String quienes = hay > 0 ? hay + " usuario(s) con avance o sello" : "usuarios con avance";
        if (n.meta() != a.meta()) r.add(new Problema("meta", "Cambiar la meta de " + a.meta() + " a " + n.meta() + " afecta a " + quienes + ": "
                + (n.meta() > a.meta() ? "los que ya la cumplieron la conservan, los demás deberán hacer más." : "algunos podrían cumplirla de inmediato.")));
        if (!a.audiencia().equals(n.audiencia())) r.add(new Problema("audiencia", "Cambiar la audiencia hace que algunos usuarios dejen de ver la misión (no pierden su avance)."));
        if (!Objects.equals(a.prerrequisitoId(), n.prerrequisitoId())) r.add(new Problema("prerrequisitoId", "Cambiar el requisito puede bloquear la misión a usuarios que ya la tenían en curso."));
        if (!Objects.equals(a.escaleraPaso(), n.escaleraPaso())) r.add(new Problema("escaleraPaso", "Cambiar el paso fijo modifica las metas de los niveles futuros."));
        return r;
    }

    /**
     * El asistente solo ofrece tres dificultades (1, 2 y 3), pero las misiones del sistema inicial tienen otros valores (por ejemplo 1.5). Si el admin no tocó la
     * dificultad, se conserva el número que ya tenía: editar un texto no debe cambiar en silencio cuál es «la más fácil de cumplir».
     */
    private static Normalizada conEsfuerzoOriginal(Normalizada n, Fila actual) {
        if (!MisionAdminReglas.dificultadDe(n.esfuerzo()).equals(MisionAdminReglas.dificultadDe(actual.esfuerzo())) || n.esfuerzo() == actual.esfuerzo()) return n;
        return new Normalizada(n.titulo(), n.descripcion(), n.descripcionCumplida(), n.categoria(), n.metrica(), n.meta(), n.unidad(), n.vigencia(), n.tipoContenido(), n.generoId(), n.directorId(), n.sagaId(), n.actorId(), n.distintas(),
                n.selloNuevo(), n.selloId(), n.nivelSello(), n.selloNombre(), n.selloDescripcion(), n.selloImagenUrl(), n.selloCategoria(), actual.esfuerzo(), n.prerrequisitoId(), n.sellosNecesarios(), n.visibilidad(), n.desde(), n.hasta(),
                n.accionTexto(), n.accionDestino(), n.orden(), n.escaleraCsv(), n.escaleraPaso(), n.escaleraSobre(), n.audiencia());
    }

    private static String sinEspacios(String s) { return s == null ? null : s.replace(" ", ""); }

    @FunctionalInterface
    private interface Comparador { void ver(String campo, Object antes, Object despues); }

    /** Lo que cambió, en una línea por campo: sirve para el historial. */
    private static String cambios(Fila a, Normalizada n, boolean retocarSello) {
        List<String> c = new ArrayList<>();
        Comparador ver = (campo, antes, despues) -> { if (!Objects.equals(antes, despues)) c.add(campo + ": " + corto(antes) + " → " + corto(despues)); };
        ver.ver("nombre", a.titulo(), n.titulo());
        ver.ver("descripción", a.descripcion(), n.descripcion());
        ver.ver("mensaje al cumplir", a.descripcionCumplida(), n.descripcionCumplida());
        ver.ver("categoría", a.categoria(), n.categoria());
        ver.ver("tipo", a.metrica(), n.metrica());
        ver.ver("contenido", a.tipoContenido(), n.tipoContenido());
        ver.ver("género", a.generoId(), n.generoId());
        ver.ver("director", a.directorId(), n.directorId());
        ver.ver("saga", a.sagaId(), n.sagaId());
        ver.ver("actor", a.actorId(), n.actorId());
        ver.ver("distintos", a.distintas(), n.distintas());
        ver.ver("meta", a.meta(), n.meta());
        ver.ver("vigencia", a.vigencia(), n.vigencia());
        ver.ver("desde", a.desde(), n.desde());
        ver.ver("hasta", a.hasta(), n.hasta());
        ver.ver("niveles", sinEspacios(a.escalera()), n.escaleraCsv());
        ver.ver("paso fijo", a.escaleraPaso(), n.escaleraPaso());
        ver.ver("visibilidad", a.visibilidad(), n.visibilidad());
        ver.ver("sellos para revelarla", a.sellosNecesarios(), n.sellosNecesarios());
        ver.ver("requisito", a.prerrequisitoId(), n.prerrequisitoId());
        ver.ver("audiencia", a.audiencia(), n.audiencia());
        ver.ver("dificultad", MisionAdminReglas.dificultadDe(a.esfuerzo()), MisionAdminReglas.dificultadDe(n.esfuerzo()));
        ver.ver("orden", a.orden(), n.orden());
        ver.ver("botón (texto)", a.accionTexto(), n.accionTexto());
        ver.ver("botón (destino)", a.accionDestino(), n.accionDestino());
        if (n.selloId() != null) ver.ver("sello asignado", a.selloId(), n.selloId());
        if (retocarSello) {
            if (n.selloNombre() != null) ver.ver("sello: nombre", a.selloNombre(), n.selloNombre());
            if (n.selloDescripcion() != null) ver.ver("sello: descripción", a.selloDescripcion(), n.selloDescripcion());
            if (n.selloCategoria() != null) ver.ver("sello: categoría", a.selloCategoria(), n.selloCategoria());
            ver.ver("sello: imagen", a.selloImagenUrl(), n.selloImagenUrl());
        }
        String r = String.join("; ", c);
        return r.length() > 900 ? r.substring(0, 897) + "..." : r;
    }

    private static String corto(Object o) {
        if (o == null) return "vacío";
        String s = String.valueOf(o);
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    /** «X (copia)», «X (copia 2)»... hasta encontrar un nombre que no exista, sin pasarse del largo permitido. */
    private static String libre(String base, int max, Predicate<String> existe) {
        for (int i = 1; i < 1000; i++) {
            String suf = i == 1 ? " (copia)" : " (copia " + i + ")";
            String t = (base.length() + suf.length() > max ? base.substring(0, max - suf.length()) : base) + suf;
            if (!existe.test(t)) return t;
        }
        throw new AdminException(409, "Hay demasiadas copias de esta misión: renombrá alguna.");
    }

    /** La misión tal como se mostró en el asistente: sirve para rellenar el formulario al editar y para volver a validarla al publicar. */
    static MisionAdminForm aFormulario(Fila f) {
        int[] esc = MisionLogica.parseEscalera(f.escalera());
        List<Integer> niveles = null;
        if (esc != null) { niveles = new ArrayList<>(); for (int x : esc) niveles.add(x); }
        return new MisionAdminForm(f.metrica(), f.tipoContenido(), f.generoId(), f.directorId(), f.sagaId(), f.actorId(), f.distintas(), f.meta(), f.vigencia(), f.desde(), f.hasta(), niveles, f.escaleraPaso(),
                f.escaleraSobre(), f.titulo(), f.descripcion(), f.descripcionCumplida(), f.categoria(), null, f.nivelSello(), f.selloNombre(), f.selloDescripcion(), f.selloImagenUrl(), f.selloCategoria(),
                f.visibilidad(), f.sellosNecesarios(), f.prerrequisitoId(), f.audiencia(), MisionAdminReglas.dificultadDe(f.esfuerzo()), f.orden(), f.accionTexto(), f.accionDestino());
    }

    /** BORRADOR (nunca se publicó) · PAUSADA (se publicó y se pausó) · PROGRAMADA (activa pero todavía no empieza) · VENCIDA (activa pero ya terminó) · ACTIVA. */
    static String estado(Fila f, LocalDateTime ahora) {
        if (!f.activa()) return f.publicadaAt() == null ? "BORRADOR" : "PAUSADA";
        if (f.hasta() != null && f.hasta().isBefore(ahora)) return "VENCIDA";
        if (f.desde() != null && f.desde().isAfter(ahora)) return "PROGRAMADA";
        return "ACTIVA";
    }

    private Map<String, Object> item(Fila f, LocalDateTime ahora) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.id()); m.put("titulo", f.titulo()); m.put("estado", estado(f, ahora)); m.put("origen", f.origen()); m.put("editable", !"AUTO".equals(f.origen()));
        m.put("categoria", f.categoria()); m.put("metrica", f.metrica());
        m.put("metricaEtiqueta", MisionAdminReglas.metrica(f.metrica()).map(Metrica::etiqueta).orElse(f.metrica()));
        m.put("vigencia", f.vigencia()); m.put("audiencia", f.audiencia()); m.put("meta", f.meta()); m.put("unidad", f.unidad()); m.put("escalera", f.escalera() != null); m.put("niveles", f.escalera());
        m.put("orden", f.orden()); m.put("desde", f.desde()); m.put("hasta", f.hasta());
        Map<String, Object> sello = new LinkedHashMap<>();
        sello.put("id", f.selloId()); sello.put("nombre", f.selloNombre()); sello.put("categoria", f.selloCategoria()); sello.put("imagenUrl", f.selloImagenUrl());
        m.put("sello", sello);
        m.put("enCurso", f.usuariosEnCurso()); m.put("cumplieron", f.usuariosCumplieron()); m.put("conProgreso", f.conProgreso());
        m.put("publicadaAt", f.publicadaAt()); m.put("actualizadaAt", f.actualizadaAt());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detalle(Fila f, LocalDateTime ahora) {
        Map<String, Object> m = item(f, ahora);
        m.put("descripcion", f.descripcion()); m.put("descripcionCumplida", f.descripcionCumplida()); m.put("tipoContenido", f.tipoContenido());
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("generoId", f.generoId()); filtros.put("directorId", f.directorId()); filtros.put("sagaId", f.sagaId()); filtros.put("actorId", f.actorId());
        m.put("filtros", filtros);
        m.put("distintas", f.distintas()); m.put("escaleraPaso", f.escaleraPaso()); m.put("escaleraSobre", f.escaleraSobre()); m.put("nivelSello", f.nivelSello());
        m.put("dificultad", MisionAdminReglas.dificultadDe(f.esfuerzo())); m.put("visibilidad", f.visibilidad()); m.put("sellosNecesarios", f.sellosNecesarios());
        m.put("prerrequisitoId", f.prerrequisitoId());
        m.put("prerrequisitoTitulo", f.prerrequisitoId() == null ? null : datos.mision(f.prerrequisitoId()).map(MisionRef::titulo).orElse(null));
        m.put("accion", f.accionTexto() == null ? null : Map.of("texto", f.accionTexto(), "destino", f.accionDestino() == null ? "" : f.accionDestino()));
        Map<String, Object> sello = new LinkedHashMap<>((Map<String, Object>) m.get("sello"));
        sello.put("descripcion", f.selloDescripcion()); sello.put("misionesQueLoUsan", f.misionesDelSello());
        m.put("sello", sello);
        m.put("bloqueados", f.conProgreso() && !"AUTO".equals(f.origen()) ? CAMPOS_BLOQUEADOS : List.of());
        m.put("creadaAt", f.creadaAt()); m.put("creadaPor", f.creadaPor()); m.put("actualizadaPor", f.actualizadaPor());
        m.put("form", aFormulario(f));
        List<Map<String, Object>> h = new ArrayList<>();
        for (HistorialItem x : datos.historial(f.id(), 30)) h.add(historialMapa(x));
        m.put("historial", h);
        return m;
    }

    private static Map<String, Object> historialMapa(HistorialItem h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.id()); m.put("usuarioId", h.usuarioId()); m.put("accion", h.accion()); m.put("detalle", h.detalle()); m.put("fecha", h.creadaAt());
        return m;
    }

    private static List<Map<String, Object>> problemas(List<Problema> l) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Problema p : l) r.add(Map.of("campo", p.campo(), "mensaje", p.mensaje()));
        return r;
    }

    private static Map<String, Object> resultadoMapa(List<Problema> errores, List<Problema> avisos) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", errores.isEmpty()); m.put("errores", problemas(errores)); m.put("avisos", problemas(avisos));
        return m;
    }

    /** Para que el controlador devuelva siempre el mismo formato de problemas. */
    public static Map<String, Object> errorMapa(AdminException e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", e.getMessage()); m.put("errores", problemas(e.errores())); m.put("avisos", problemas(e.avisos()));
        return m;
    }
}
