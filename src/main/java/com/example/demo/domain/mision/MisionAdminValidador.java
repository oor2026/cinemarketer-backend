package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionAdminReglas.Metrica;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Decide si lo que el admin completó se puede publicar. Devuelve ERRORES (no se puede guardar así) y AVISOS (se puede, pero conviene que lo sepa),
 * cada uno atado al campo del asistente que corresponde. Si no hay errores, devuelve además la misión NORMALIZADA: ya limpia, con los valores por defecto
 * puestos y lista para guardarse. Java puro: lo que necesita de la base o de TMDB lo pregunta a través de {@link Entorno}.
 */
public final class MisionAdminValidador {
    private MisionAdminValidador() {}

    /** campo = el nombre del campo del formulario ("" si el aviso es general). */
    public record Problema(String campo, String mensaje) {}

    public record SelloRef(long id, String nombre, int misionesQueLoUsan, boolean alguienLoGano) {}

    public record MisionRef(long id, String titulo, boolean activa, boolean automatica, Long prerrequisitoId) {}

    /** Lo que el validador necesita saber de afuera. null en lo de TMDB = «no se pudo verificar». */
    public interface Entorno {
        boolean tituloExiste(String titulo, Long excluirMisionId);
        boolean selloNombreExiste(String nombre, Long excluirSelloId);
        Optional<SelloRef> sello(long id);
        Optional<MisionRef> mision(long id);
        /** El menor esfuerzo (meta × dificultad) entre las misiones activas y visibles para esa audiencia; null si no hay ninguna. */
        Double esfuerzoMinimoActivas(Long excluirMisionId, String audiencia);
        Integer estrenadasSaga(long sagaId);
        Integer peliculasActor(long personaId);
        Integer peliculasDirector(long personaId);
        LocalDateTime ahora();
    }

    /** La misión lista para guardarse. selloNuevo = hay que crear el sello; si no, se usa selloId (y los datos del sello que no sean null lo actualizan). */
    public record Normalizada(String titulo, String descripcion, String descripcionCumplida, String categoria, String metrica, int meta, String unidad, String vigencia,
                              String tipoContenido, Long generoId, Long directorId, Long sagaId, Long actorId, boolean distintas,
                              boolean selloNuevo, Long selloId, int nivelSello, String selloNombre, String selloDescripcion, String selloImagenUrl, String selloCategoria,
                              double esfuerzo, Long prerrequisitoId, Integer sellosNecesarios, String visibilidad, LocalDateTime desde, LocalDateTime hasta,
                              String accionTexto, String accionDestino, int orden, String escaleraCsv, Integer escaleraPaso, String escaleraSobre, String audiencia) {}

    public record Resultado(Normalizada normalizada, List<Problema> errores, List<Problema> avisos) {
        public boolean ok() { return errores.isEmpty(); }
    }

    public static final String PREFIJO_IMAGEN = "https://res.cloudinary.com/";
    public static final String CARPETA_IMAGEN = "/misiones/sellos/";

    /**
     * @param misionId       null al crear; el id al editar (para no chocar con ella misma)
     * @param selloActualId  el sello que ya tiene la misión al editar (null al crear)
     */
    public static Resultado validar(MisionAdminForm f, Long misionId, Long selloActualId, Entorno e) {
        List<Problema> err = new ArrayList<>(), av = new ArrayList<>();
        LocalDateTime ahora = e.ahora();

        // ---- qué se le pide al usuario
        String codigo = f.metrica() == null ? "" : f.metrica().trim().toUpperCase();
        Metrica m = MisionAdminReglas.metrica(codigo).orElse(null);
        if (m == null) err.add(new Problema("metrica", "Elegí qué se le pide al usuario."));

        // ---- nombre y textos
        String titulo = limpio(f.titulo());
        if (titulo.isEmpty()) err.add(new Problema("titulo", "Poné un nombre para la misión."));
        else if (titulo.length() > 100) err.add(new Problema("titulo", "El nombre puede tener hasta 100 caracteres (tiene " + titulo.length() + ")."));
        else if (e.tituloExiste(titulo, misionId)) err.add(new Problema("titulo", "Ya existe una misión con ese nombre."));
        String descripcion = limpio(f.descripcion());
        if (descripcion.isEmpty()) err.add(new Problema("descripcion", "Contale al usuario qué tiene que hacer."));
        else if (descripcion.length() > 255) err.add(new Problema("descripcion", "La descripción puede tener hasta 255 caracteres (tiene " + descripcion.length() + ")."));
        String cumplida = limpio(f.descripcionCumplida());
        if (cumplida.length() > 160) err.add(new Problema("descripcionCumplida", "El mensaje al cumplirla puede tener hasta 160 caracteres (tiene " + cumplida.length() + ")."));

        // ---- película o serie, y filtros
        String tipo = f.tipoContenido() == null || f.tipoContenido().isBlank() ? null : f.tipoContenido().trim().toUpperCase();
        if (tipo != null && !tipo.equals("PELICULA") && !tipo.equals("SERIE")) { err.add(new Problema("tipoContenido", "El tipo de contenido tiene que ser película o serie.")); tipo = null; }
        Long genero = id(f.generoId(), "generoId", err), director = id(f.directorId(), "directorId", err), saga = id(f.sagaId(), "sagaId", err), actor = id(f.actorId(), "actorId", err);
        int filtros = (genero != null ? 1 : 0) + (director != null ? 1 : 0) + (saga != null ? 1 : 0) + (actor != null ? 1 : 0);
        if (m != null && tipo != null && !m.tipoContenido()) err.add(new Problema("tipoContenido", "«" + m.etiqueta() + "» no distingue entre películas y series."));
        if (m != null && filtros > 0 && !m.filtrosPelicula()) err.add(new Problema("generoId", "«" + m.etiqueta() + "» no admite filtros de género, director, saga ni actor/actriz."));
        if (filtros > 0) {
            if ("SERIE".equals(tipo)) err.add(new Problema("tipoContenido", "Los filtros (género, director, saga y actor/actriz) solo existen para películas: no se pueden combinar con series."));
            else tipo = "PELICULA";
        }
        if (filtros > 1) av.add(new Problema("generoId", "Combinar varios filtros achica mucho las películas posibles. Asegurate de que existan."));

        boolean distintas = Boolean.TRUE.equals(f.distintas());
        if (m != null && distintas && !m.distintas() && !"GUSTOS".equals(m.codigo())) {
            // No es un error: una misión que ya está guardada puede traerlo (las del sistema inicial), y bloquearla impediría editarla o publicarla. Se ignora y se avisa.
            av.add(new Problema("distintas", "VOTOS".equals(m.codigo()) ? "Los votos ya cuentan una sola vez por título: «contar distintos» no hace falta y se ignora." : "«" + m.etiqueta() + "» no usa «contar distintos»: se ignora."));
            distintas = false;
        }
        if (m != null && "GUSTOS".equals(m.codigo())) distintas = true;            // cada espacio de gustos cuenta una sola vez (siempre, aunque no lo tilden)

        // ---- vigencia y fechas
        String vig = f.vigencia() == null ? "" : f.vigencia().trim().toUpperCase();
        if (vig.isEmpty() && m != null && m.vigencias().size() == 1) vig = m.vigencias().get(0);
        if (vig.isEmpty()) err.add(new Problema("vigencia", "Elegí cada cuánto se renueva o cuánto dura la misión."));
        else if (!MisionAdminReglas.VIGENCIAS.contains(vig)) err.add(new Problema("vigencia", "La vigencia elegida no existe."));
        else if (m != null && !m.vigencias().contains(vig)) err.add(new Problema("vigencia", "Para «" + m.etiqueta() + "» la vigencia puede ser: " + String.join(", ", m.vigencias()).toLowerCase() + "."));
        LocalDateTime desde = f.desde(), hasta = f.hasta();
        if ("EVENTO".equals(vig) && (desde == null || hasta == null)) err.add(new Problema("hasta", "Un evento necesita fecha de inicio y de fin."));
        if (desde != null && hasta != null && !hasta.isAfter(desde)) err.add(new Problema("hasta", "La fecha de fin tiene que ser posterior a la de inicio."));
        if (hasta != null && !hasta.isAfter(ahora)) {
            if (misionId == null) err.add(new Problema("hasta", "La fecha de fin ya pasó."));
            else av.add(new Problema("hasta", "La fecha de fin ya pasó: la misión figurará como vencida."));
        }
        if ("EVENTO".equals(vig) && desde != null && desde.isBefore(ahora)) av.add(new Problema("desde", "El evento empieza en el pasado: va a estar disponible apenas lo publiques."));

        // ---- niveles (escalera)
        List<Integer> esc = f.escalera() == null ? List.of() : f.escalera();
        String sobre = "PROGRESO", escCsv = null;
        Integer paso = null, primerNivel = null;
        if (!esc.isEmpty()) {
            if (m != null && !m.escalera()) err.add(new Problema("escalera", "«" + m.etiqueta() + "» no admite niveles."));
            else {
                boolean bien = true;
                if (esc.size() > 30) { err.add(new Problema("escalera", "Máximo 30 niveles.")); bien = false; }
                for (int i = 0; i < esc.size() && bien; i++) {
                    Integer x = esc.get(i);
                    if (x == null || x <= 0) { err.add(new Problema("escalera", "Cada nivel tiene que ser un número mayor que 0.")); bien = false; }
                    else if (i > 0 && x <= esc.get(i - 1)) { err.add(new Problema("escalera", "Los niveles tienen que ir creciendo: cada uno mayor que el anterior.")); bien = false; }
                    else if (m != null && x > m.metaMax()) { err.add(new Problema("escalera", "El nivel " + x + " supera el máximo razonable para «" + m.etiqueta() + "» (" + m.metaMax() + ")." )); bien = false; }
                }
                sobre = f.escaleraSobre() == null || f.escaleraSobre().isBlank() ? ("HOY".equals(vig) || "SEMANA".equals(vig) ? "VECES" : "PROGRESO") : f.escaleraSobre().trim().toUpperCase();
                boolean ciclo = "HOY".equals(vig) || "SEMANA".equals(vig);
                if (!sobre.equals("PROGRESO") && !sobre.equals("VECES")) err.add(new Problema("escaleraSobre", "Los niveles son por progreso o por veces cumplida."));
                else if (sobre.equals("VECES") && !ciclo) err.add(new Problema("escaleraSobre", "Los niveles por «veces cumplida» solo existen en misiones de hoy o de la semana."));
                else if (sobre.equals("PROGRESO") && ciclo) err.add(new Problema("escaleraSobre", "En una misión de hoy o de la semana los niveles son por veces cumplida: se repite cada día o cada semana."));
                if (f.escaleraPaso() != null) {
                    if (!sobre.equals("PROGRESO")) err.add(new Problema("escaleraPaso", "El paso fijo solo se usa en niveles por progreso."));
                    else if (f.escaleraPaso() < 1) err.add(new Problema("escaleraPaso", "El paso fijo tiene que ser al menos 1."));
                    else paso = f.escaleraPaso();
                }
                if (bien) { escCsv = String.join(",", esc.stream().map(String::valueOf).toList()); primerNivel = esc.get(0); }
            }
        } else if (f.escaleraPaso() != null) {
            err.add(new Problema("escaleraPaso", "Para usar un paso fijo primero definí los niveles."));
        }

        // ---- meta
        Integer meta = f.meta();
        if (primerNivel != null && "PROGRESO".equals(sobre)) {
            if (meta != null && !meta.equals(primerNivel)) av.add(new Problema("meta", "Con niveles por progreso la meta es el primer nivel (" + primerNivel + "): se ignora el valor que pusiste."));
            meta = primerNivel;
        } else if (meta == null) err.add(new Problema("meta", "Poné cuánto tiene que hacer el usuario."));
        else if (meta < 1) err.add(new Problema("meta", "La meta tiene que ser mayor que 0."));
        else if (m != null) {
            int tope = "GUSTOS".equals(m.codigo()) ? (tipo != null ? 4 : 8) : m.metaMax();
            if (meta > tope) err.add(new Problema("meta", "GUSTOS".equals(m.codigo()) ? "Hay solo " + tope + " espacios de gustos" + (tipo != null ? " de " + (tipo.equals("PELICULA") ? "películas" : "series") : "") + ": la meta no puede ser mayor." : "La meta no puede superar " + tope + " " + m.unidad() + "."));
        }
        if (meta != null && meta < 1) meta = null;

        // ---- ¿la meta se puede cumplir? (TMDB)
        if (m != null && meta != null && (("VOTOS".equals(m.codigo())) || distintas)) {
            int ultimo = escCsv != null ? esc.get(esc.size() - 1) : meta;       // solo se mira la lista de niveles si es válida
            if (saga != null) alcanzable("sagaId", e.estrenadasSaga(saga), meta, ultimo, "la saga", err, av);
            if (actor != null) alcanzable("actorId", e.peliculasActor(actor), meta, ultimo, "ese actor o actriz", err, av);
            if (director != null) alcanzable("directorId", e.peliculasDirector(director), meta, ultimo, "ese director", err, av);
        }

        // ---- categoría y sello
        String categoria = f.categoria() == null || f.categoria().isBlank() ? MisionAdminReglas.categoriaPorDefecto(codigo, saga != null, actor != null, director != null, genero != null) : f.categoria().trim().toLowerCase();
        if (!MisionAdminReglas.CATEGORIAS.contains(categoria)) { err.add(new Problema("categoria", "La categoría elegida no existe.")); categoria = "especial"; }
        Long selloId = null; boolean selloNuevo = false; int nivel = 1;
        String sNombre = null, sDesc = null, sImg = null, sCat = null;
        if (f.selloExistenteId() != null) {                                      // reutiliza el sello de otra misión: no se toca
            Optional<SelloRef> s = e.sello(f.selloExistenteId());
            if (s.isEmpty()) err.add(new Problema("selloExistenteId", "El sello elegido no existe."));
            else {
                selloId = s.get().id();
                if (s.get().misionesQueLoUsan() > 0 && !Objects.equals(selloId, selloActualId)) av.add(new Problema("selloExistenteId", "El sello «" + s.get().nombre() + "» ya lo usan " + s.get().misionesQueLoUsan() + " misión(es): quien ya lo tenga lo va a ver ganado."));
            }
            nivel = f.nivelSello() == null ? 1 : f.nivelSello();
            if (nivel < 1 || nivel > 50) { err.add(new Problema("nivelSello", "El nivel del sello tiene que estar entre 1 y 50.")); nivel = 1; }
        } else {
            boolean mantiene = misionId != null && selloActualId != null;        // al editar sin elegir otro, se conserva el sello y se puede retocar
            selloNuevo = !mantiene;
            if (mantiene) selloId = selloActualId;
            sNombre = limpio(f.selloNombre());
            if (sNombre.isEmpty()) { if (selloNuevo) err.add(new Problema("selloNombre", "Poné el nombre del sello.")); sNombre = null; }
            else if (sNombre.length() > 80) err.add(new Problema("selloNombre", "El nombre del sello puede tener hasta 80 caracteres (tiene " + sNombre.length() + ")."));
            else if (e.selloNombreExiste(sNombre, selloNuevo ? null : selloId)) err.add(new Problema("selloNombre", "Ya existe un sello con ese nombre. Si querés reutilizarlo, elegilo en «Usar un sello existente»."));
            sDesc = limpio(f.selloDescripcion());
            if (sDesc.length() > 255) err.add(new Problema("selloDescripcion", "La descripción del sello puede tener hasta 255 caracteres (tiene " + sDesc.length() + ")."));
            if (sDesc.isEmpty()) sDesc = selloNuevo && !cumplida.isEmpty() ? cumplida : null;
            sImg = imagen(f.selloImagenUrl(), err);
            sCat = f.selloCategoria() == null || f.selloCategoria().isBlank() ? (selloNuevo ? categoria : null) : f.selloCategoria().trim().toLowerCase();
            if (sCat != null && !MisionAdminReglas.CATEGORIAS.contains(sCat)) { err.add(new Problema("selloCategoria", "La categoría del sello no existe.")); sCat = null; }
            if (!esc.isEmpty()) nivel = 1;
        }
        if (!esc.isEmpty()) nivel = 1;                                           // con niveles, el sello sube solo

        // ---- visibilidad
        String vis = f.visibilidad() == null || f.visibilidad().isBlank() ? "VISIBLE" : f.visibilidad().trim().toUpperCase();
        if (!MisionAdminReglas.VISIBILIDADES.contains(vis)) { err.add(new Problema("visibilidad", "La visibilidad elegida no existe.")); vis = "VISIBLE"; }
        Integer sellosNec = null;
        if ("MISTERIOSA".equals(vis)) {
            sellosNec = f.sellosNecesarios();
            if (sellosNec == null || sellosNec < 1) { err.add(new Problema("sellosNecesarios", "Una misión misteriosa necesita saber cuántos sellos hacen falta para revelarla (al menos 1).")); sellosNec = null; }
            else if (sellosNec > 500) err.add(new Problema("sellosNecesarios", "Pediste revelarla con " + sellosNec + " sellos: nadie llega a tantos. El máximo es 500."));
        }

        // ---- encadenada
        Long pre = f.prerrequisitoId();
        if (pre != null) {
            if (pre.equals(misionId)) err.add(new Problema("prerrequisitoId", "Una misión no puede ser su propio requisito."));
            else {
                Optional<MisionRef> p = e.mision(pre);
                if (p.isEmpty()) err.add(new Problema("prerrequisitoId", "La misión previa elegida no existe."));
                else if (p.get().automatica()) err.add(new Problema("prerrequisitoId", "La misión previa es de la rotación automática: cada usuario tiene la suya, así que no sirve como requisito."));
                else {
                    Set<Long> vistos = new HashSet<>(); Long actual = p.get().prerrequisitoId(); int vueltas = 0; boolean ciclo = false;
                    while (actual != null && vueltas++ < 25) {
                        if (misionId != null && actual.equals(misionId)) { ciclo = true; break; }
                        if (!vistos.add(actual)) break;
                        actual = e.mision(actual).map(MisionRef::prerrequisitoId).orElse(null);
                    }
                    if (ciclo) err.add(new Problema("prerrequisitoId", "Eso crea un ciclo: la misión previa ya depende de esta, y no se podría desbloquear ninguna."));
                    else if (!p.get().activa()) av.add(new Problema("prerrequisitoId", "La misión previa «" + p.get().titulo() + "» está pausada o en borrador: esta quedará bloqueada hasta que la publiques."));
                }
            }
        }

        // ---- audiencia, dificultad, orden y botón
        String aud = f.audiencia() == null || f.audiencia().isBlank() ? "TODOS" : f.audiencia().trim().toUpperCase();
        if (!MisionAdminReglas.AUDIENCIAS.contains(aud)) { err.add(new Problema("audiencia", "La audiencia elegida no existe.")); aud = "TODOS"; }
        if (m != null && "SORTEOS".equals(m.codigo()) && !"PREMIUM".equals(aud)) av.add(new Problema("audiencia", "Hoy solo los usuarios Premium pueden anotarse a sorteos. Para el resto esta misión no se puede cumplir: elegí «Solo Premium»."));
        String dif = f.dificultad() == null || f.dificultad().isBlank() ? "MEDIA" : f.dificultad().trim().toUpperCase();
        if (!MisionAdminReglas.DIFICULTADES.contains(dif)) { err.add(new Problema("dificultad", "La dificultad elegida no existe.")); dif = "MEDIA"; }
        int orden = f.orden() == null ? 200 : f.orden();
        if (orden < 0 || orden > 9999) { err.add(new Problema("orden", "El orden tiene que estar entre 0 y 9999.")); orden = 200; }
        String aTexto = limpio(f.accionTexto()), aDestino = limpio(f.accionDestino());
        if (aDestino.startsWith("#")) aDestino = aDestino.substring(1);
        if (aTexto.length() > 60) err.add(new Problema("accionTexto", "El texto del botón puede tener hasta 60 caracteres (tiene " + aTexto.length() + ")."));
        if (!aDestino.isEmpty() && !MisionAdminReglas.DESTINOS.containsKey(aDestino)) err.add(new Problema("accionDestino", "Elegí una de las pantallas de la lista."));
        else if (!aTexto.isEmpty() && aDestino.isEmpty()) err.add(new Problema("accionDestino", "Elegí a dónde lleva el botón."));
        else if (aTexto.isEmpty() && !aDestino.isEmpty()) aTexto = "Ir ahora";

        // ---- avisos generales
        double esfuerzo = MisionAdminReglas.esfuerzo(dif);
        if (meta != null) {
            Double minimo = e.esfuerzoMinimoActivas(misionId, aud);
            double propio = meta * esfuerzo;
            if (minimo != null && propio < minimo) av.add(new Problema("dificultad", "Con esta configuración sería la misión MÁS FÁCIL de todas, así que todos los usuarios la verían destacada en el inicio (esfuerzo " + fmt(propio) + " contra " + fmt(minimo) + " de la siguiente). Subí la meta o la dificultad si no es lo que querés."));
        }
        av.add(new Problema("", "Cuenta desde que se publica: nadie recibe progreso por lo que hizo antes."));

        if (!err.isEmpty() || m == null) return new Resultado(null, err, av);
        return new Resultado(new Normalizada(titulo, descripcion, cumplida.isEmpty() ? null : cumplida, categoria, m.codigo(), meta, m.unidad(), vig, tipo, genero, director, saga, actor, distintas,
                selloNuevo, selloId, nivel, sNombre, sDesc, sImg, sCat, esfuerzo, pre, sellosNec, vis, desde, hasta,
                aTexto.isEmpty() ? null : aTexto, aDestino.isEmpty() ? null : aDestino, orden, escCsv, paso, escCsv == null ? "PROGRESO" : sobre, aud), err, av);
    }

    // ------------------------------------------------------------------------------------------------ utilidades
    private static void alcanzable(String campo, Integer disponibles, int meta, int ultimo, String que, List<Problema> err, List<Problema> av) {
        if (disponibles == null) av.add(new Problema(campo, "No pudimos verificar con TMDB cuántas películas tiene " + que + ": confirmá que la meta se puede cumplir."));
        else if (disponibles < meta) err.add(new Problema(campo, "Tiene solo " + disponibles + " película(s) estrenada(s) y la meta es " + meta + ": no se podría cumplir."));
        else if (disponibles < ultimo) av.add(new Problema(campo, "Tiene " + disponibles + " película(s) estrenada(s): los niveles más altos (hasta " + ultimo + ") no se van a poder cumplir."));
    }

    private static Long id(Long v, String campo, List<Problema> err) {
        if (v == null) return null;
        if (v <= 0) { err.add(new Problema(campo, "El identificador elegido no es válido.")); return null; }
        return v;
    }

    /** Sin caracteres de control, con los espacios normalizados. */
    static String limpio(String s) {
        return s == null ? "" : s.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    /** Solo se aceptan imágenes subidas desde el panel (Cloudinary, carpeta de sellos): nada de enlaces a cualquier lado. */
    private static String imagen(String url, List<Problema> err) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        if (u.length() > 500 || u.matches(".*\\s.*") || !u.startsWith(PREFIJO_IMAGEN) || !u.contains(CARPETA_IMAGEN)) {
            err.add(new Problema("selloImagenUrl", "La imagen tiene que subirse desde el panel (botón «Subir imagen»): no se aceptan enlaces de otros sitios."));
            return null;
        }
        return u;
    }

    private static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
}
