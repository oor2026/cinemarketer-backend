package com.example.demo.domain.mision;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Las REGLAS de las misiones, sin Spring ni base de datos: qué estado tiene una misión para un usuario, cuánto avanzó, cuál es
 * «la más fácil de cumplir» y cómo se arma cada respuesta JSON. Al ser Java puro, se prueba sola (MisionLogicaTest).
 *
 * ESCALERAS: una misión puede tener una escalera de metas (10, 25, 50, 100...). Es UNA sola misión que se rearma: al cumplir un nivel
 * el sello sube de nivel y la misión pasa a pedir la meta siguiente. Hay dos tipos:
 *   - PROGRESO: la escalera es sobre el avance ACUMULADO (votos, comentarios, racha...). El conteo no se reinicia al subir de nivel.
 *   - VECES: la escalera es sobre las VECES que se cumplió una misión que se repite (la del día o la de la semana).
 * Después del último escalón, si hay «paso fijo» la escalera sigue sola (+paso, +paso...); si no, la misión queda ganada.
 */
public final class MisionLogica {

    private MisionLogica() {}

    // ------------------------------------------------------------------------------------------------ Datos
    /** imagenUrl: la imagen propia del sello (la sube el admin). null = se dibuja con el ícono y el color de su categoría. */
    public record Sello(long id, String nombre, String categoria, String descripcion, String imagenUrl, String fondoUrl) {
        public Sello(long id, String nombre, String categoria, String descripcion) { this(id, nombre, categoria, descripcion, null, null); }
        public Sello(long id, String nombre, String categoria, String descripcion, String imagenUrl) { this(id, nombre, categoria, descripcion, imagenUrl, null); }
        public boolean tieneFondo() { return fondoUrl != null && !fondoUrl.isBlank(); }
        public boolean tieneImagen() { return imagenUrl != null && !imagenUrl.isBlank(); }
    }

    public record Mision(long id, String titulo, String descripcion, String descripcionCumplida, String categoria, String metrica,
                         int meta, String unidad, String vigencia, String tipoContenido, Long generoId, Long directorId, Long sagaId, Long actorId,
                         boolean distintas, Sello sello, int nivelSello, double esfuerzoPorPaso, Long prerrequisitoId, Integer sellosNecesarios,
                         String visibilidad, LocalDateTime desde, LocalDateTime hasta, String accionTexto, String accionDestino, int orden,
                         int[] escalera, Integer escaleraPaso, String escaleraSobre, String familia, String audiencia, Long serieId) {

        /** Misión que no es de una serie puntual (así se creaban hasta ahora). */
        public Mision(long id, String titulo, String descripcion, String descripcionCumplida, String categoria, String metrica,
                      int meta, String unidad, String vigencia, String tipoContenido, Long generoId, Long directorId, Long sagaId, Long actorId,
                      boolean distintas, Sello sello, int nivelSello, double esfuerzoPorPaso, Long prerrequisitoId, Integer sellosNecesarios,
                      String visibilidad, LocalDateTime desde, LocalDateTime hasta, String accionTexto, String accionDestino, int orden,
                      int[] escalera, Integer escaleraPaso, String escaleraSobre, String familia, String audiencia) {
            this(id, titulo, descripcion, descripcionCumplida, categoria, metrica, meta, unidad, vigencia, tipoContenido, generoId, directorId, sagaId, actorId,
                    distintas, sello, nivelSello, esfuerzoPorPaso, prerrequisitoId, sellosNecesarios, visibilidad, desde, hasta, accionTexto, accionDestino, orden,
                    escalera, escaleraPaso, escaleraSobre, familia, audiencia, null);
        }

        /** Misión para todos los usuarios (así se creaban hasta ahora). */
        public Mision(long id, String titulo, String descripcion, String descripcionCumplida, String categoria, String metrica, int meta, String unidad, String vigencia, String tipoContenido, Long generoId, Long directorId, Long sagaId, Long actorId, boolean distintas, Sello sello, int nivelSello, double esfuerzoPorPaso, Long prerrequisitoId, Integer sellosNecesarios, String visibilidad, LocalDateTime desde, LocalDateTime hasta, String accionTexto, String accionDestino, int orden, int[] escalera, Integer escaleraPaso, String escaleraSobre, String familia) {
            this(id, titulo, descripcion, descripcionCumplida, categoria, metrica, meta, unidad, vigencia, tipoContenido, generoId, directorId, sagaId, actorId, distintas, sello, nivelSello, esfuerzoPorPaso, prerrequisitoId, sellosNecesarios, visibilidad, desde, hasta, accionTexto, accionDestino, orden, escalera, escaleraPaso, escaleraSobre, familia, "TODOS");
        }

        /** Misión que no es rotativa (así se creaban hasta ahora). */
        public Mision(long id, String titulo, String descripcion, String descripcionCumplida, String categoria, String metrica,
                      int meta, String unidad, String vigencia, String tipoContenido, Long generoId, Long directorId, Long sagaId, Long actorId,
                      boolean distintas, Sello sello, int nivelSello, double esfuerzoPorPaso, Long prerrequisitoId, Integer sellosNecesarios,
                      String visibilidad, LocalDateTime desde, LocalDateTime hasta, String accionTexto, String accionDestino, int orden,
                      int[] escalera, Integer escaleraPaso, String escaleraSobre) {
            this(id, titulo, descripcion, descripcionCumplida, categoria, metrica, meta, unidad, vigencia, tipoContenido, generoId, directorId, sagaId, actorId,
                    distintas, sello, nivelSello, esfuerzoPorPaso, prerrequisitoId, sellosNecesarios, visibilidad, desde, hasta, accionTexto, accionDestino, orden,
                    escalera, escaleraPaso, escaleraSobre, null);
        }

        /** Misión sin escalera. */
        public Mision(long id, String titulo, String descripcion, String descripcionCumplida, String categoria, String metrica,
                      int meta, String unidad, String vigencia, String tipoContenido, Long generoId, Long directorId, Long sagaId, Long actorId,
                      boolean distintas, Sello sello, int nivelSello, double esfuerzoPorPaso, Long prerrequisitoId, Integer sellosNecesarios,
                      String visibilidad, LocalDateTime desde, LocalDateTime hasta, String accionTexto, String accionDestino, int orden) {
            this(id, titulo, descripcion, descripcionCumplida, categoria, metrica, meta, unidad, vigencia, tipoContenido, generoId, directorId, sagaId, actorId,
                    distintas, sello, nivelSello, esfuerzoPorPaso, prerrequisitoId, sellosNecesarios, visibilidad, desde, hasta, accionTexto, accionDestino, orden,
                    null, null, "PROGRESO");
        }

        /** Misión sin filtro de saga ni de actor/actriz y sin escalera (así se creaban al principio). */
        public Mision(long id, String titulo, String descripcion, String descripcionCumplida, String categoria, String metrica,
                      int meta, String unidad, String vigencia, String tipoContenido, Long generoId, Long directorId, boolean distintas,
                      Sello sello, int nivelSello, double esfuerzoPorPaso, Long prerrequisitoId, Integer sellosNecesarios,
                      String visibilidad, LocalDateTime desde, LocalDateTime hasta, String accionTexto, String accionDestino, int orden) {
            this(id, titulo, descripcion, descripcionCumplida, categoria, metrica, meta, unidad, vigencia, tipoContenido, generoId, directorId, null, null,
                    distintas, sello, nivelSello, esfuerzoPorPaso, prerrequisitoId, sellosNecesarios, visibilidad, desde, hasta, accionTexto, accionDestino, orden);
        }

        public boolean necesitaTmdb() { return generoId != null || directorId != null || sagaId != null || actorId != null; }

        /** Las misiones de gustos siempre cuentan espacios distintos: guardar dos veces el mismo no suma. */
        public boolean cuentaDistintos() { return distintas || "GUSTOS".equals(metrica) || esDeSerie(); }

        /**
         * Misión de una serie puntual (familia SERIE): pide 3 pasos sobre ESA serie, en cualquier orden: votarla, comentarla y recomendarla.
         * Cada paso cuenta una sola vez (ver claveDistinta), así que la misión avanza de 0 a 3 pasos distintos.
         */
        public boolean esDeSerie() { return serieId != null; }

        /** Misión de una familia que rota (saga, actor/actriz, director o género): cada usuario tiene la suya y puede descartarla. */
        public boolean esRotativa() { return familia != null; }

        public boolean esEscalera() { return escalera != null && escalera.length > 0; }

        /** Escalera sobre las veces que se cumplió una misión que se repite (la del día o la de la semana). */
        public boolean escaleraSobreVeces() { return esEscalera() && "VECES".equals(escaleraSobre); }

        /**
         * La meta del escalón que se está persiguiendo, dado cuántos escalones ya se lograron. -1 si la escalera terminó.
         * Sin escalera, es simplemente la meta de la misión.
         */
        public int metaDelNivel(int logrados) {
            if (!esEscalera()) return meta;
            if (logrados < escalera.length) return escalera[logrados];
            if (escaleraPaso != null && escaleraPaso > 0) return escalera[escalera.length - 1] + (logrados - escalera.length + 1) * escaleraPaso;
            return -1;
        }
    }

    /** «10,25,50» → {10, 25, 50}. Vacío, con ceros o desordenado → null (se trata como una misión sin escalera). */
    public static int[] parseEscalera(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            String[] partes = texto.trim().split("\\s*,\\s*");
            int[] r = new int[partes.length];
            for (int i = 0; i < partes.length; i++) {
                r[i] = Integer.parseInt(partes[i]);
                if (r[i] <= 0 || (i > 0 && r[i] <= r[i - 1])) return null;
            }
            return r;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * El avance guardado de un usuario en una misión (una fila de usuario_mision).
     * nivel = escalones logrados (solo en la fila 'P' de las misiones con escalera); veces = veces cumplida (idem, en las de VECES).
     */
    public record Fila(long id, long misionId, String ciclo, int progreso, String estado, int nivel, int veces) {
        public Fila(long id, long misionId, String ciclo, int progreso, String estado) { this(id, misionId, ciclo, progreso, estado, 0, 0); }
    }

    public enum Estado { OCULTA, GANADA, EXPIRADA, EN_CURSO, CUMPLIDA_CICLO, BLOQUEADA, PROGRAMADA, SIGUIENTE, MISTERIOSA }

    /** Las cinco familias que rotan. */
    public static final List<String> FAMILIAS = List.of("SAGA", "ACTOR", "DIRECTOR", "GENERO", "SERIE");

    /**
     * El objetivo de un usuario en una familia. estado: ACTUAL (la misión que se muestra) · SIGUIENTE (la que viene, ya sorteada) ·
     * COMPLETADO · DESCARTADO (lo sacó el usuario con la ✕) · CERRADO (lo sacó el admin o dejó de ser válido).
     */
    public record Asignacion(long id, long objetivoId, long misionId, String familia, String estado, String nombre) {}

    /** Un objetivo del catálogo que todavía se le puede ofrecer al usuario. */
    public record Candidato(long objetivoId, long misionId) {}

    /** «saga», «actor o actriz»... para los textos. */
    public static String etiquetaFamilia(String familia) {
        return switch (familia == null ? "" : familia) { case "SAGA" -> "saga"; case "ACTOR" -> "actor o actriz"; case "DIRECTOR" -> "director"; case "GENERO" -> "género"; case "SERIE" -> "serie"; default -> ""; };
    }

    /**
     * Todo lo que se sabe de un usuario en un momento. filaPorMision ya trae SOLO la fila del ciclo actual de cada misión;
     * contadorPorMision trae la fila 'P' (el contador permanente: nivel y veces) de las misiones con escalera.
     */
    public record Contexto(Map<Long, Integer> nivelPorSello, Map<Long, Fila> filaPorMision, Map<Long, Fila> contadorPorMision, Map<Long, String> asignaciones,
                           Set<LocalDate> diasActivos, LocalDate hoy, LocalDateTime ahora, Map<Long, Mision> misiones) {
        /** Sin asignaciones de rotación. */
        public Contexto(Map<Long, Integer> nivelPorSello, Map<Long, Fila> filaPorMision, Map<Long, Fila> contadorPorMision, Set<LocalDate> diasActivos,
                        LocalDate hoy, LocalDateTime ahora, Map<Long, Mision> misiones) {
            this(nivelPorSello, filaPorMision, contadorPorMision, Map.of(), diasActivos, hoy, ahora, misiones);
        }
        public Contexto(Map<Long, Integer> nivelPorSello, Map<Long, Fila> filaPorMision, Set<LocalDate> diasActivos,
                        LocalDate hoy, LocalDateTime ahora, Map<Long, Mision> misiones) {
            this(nivelPorSello, filaPorMision, Map.of(), Map.of(), diasActivos, hoy, ahora, misiones);
        }
        public int totalSellos() { return nivelPorSello.size(); }
    }

    /**
     * Una misión vista por un usuario. meta = la meta que se muestra (la del escalón actual en las escaleras de PROGRESO; la del
     * día o la semana en las de VECES). nivel = el escalón que se está persiguiendo (0 si no tiene escalera). veces/metaVeces: en las
     * de VECES, cuántas veces se cumplió y cuántas hacen falta para el próximo nivel.
     */
    public record Vista(Mision mision, Estado estado, int progreso, int meta, int nivel, int veces, int metaVeces) {
        public Vista(Mision mision, Estado estado, int progreso) { this(mision, estado, progreso, mision.meta(), 0, 0, 0); }
        public int faltan() { return Math.max(0, meta - progreso); }
        public double esfuerzoRestante() { return faltan() * mision.esfuerzoPorPaso(); }
        public double porcentaje() { return meta == 0 ? 0 : progreso * 100.0 / meta; }
    }

    /** Una celebración pendiente. tipo NIVEL = pantalla de «Misión cumplida» / «Subiste de nivel»; LIVIANA = el cartelito «Cumplida hoy». */
    public record Cumplida(long id, long misionId, int nivel, String tipo, LocalDateTime cumplidaAt) {
        public Cumplida(long id, long misionId, LocalDateTime cumplidaAt) { this(id, misionId, 1, "NIVEL", cumplidaAt); }
        public boolean esLiviana() { return "LIVIANA".equals(tipo); }
    }

    public record SelloGanado(long id, String nombre, String categoria, String descripcion, int nivel, LocalDateTime ganadoAt, String imagenUrl) {
        public SelloGanado(long id, String nombre, String categoria, String descripcion, int nivel, LocalDateTime ganadoAt) { this(id, nombre, categoria, descripcion, nivel, ganadoAt, null); }
    }

    /** ¿Esta misión le corresponde a este usuario? TODOS: sí · PREMIUM: solo si es Premium · FREE: solo si NO es Premium. */
    public static boolean audienciaPermite(String audiencia, boolean premium) {
        if (audiencia == null || "TODOS".equals(audiencia)) return true;
        return "PREMIUM".equals(audiencia) ? premium : "FREE".equals(audiencia) && !premium;
    }

    // ------------------------------------------------------------------------------------------------ Ciclos y racha
    /** 'P' para lo permanente, la fecha para la misión del día, el año-semana ISO para la de la semana. */
    public static String ciclo(String vigencia, LocalDate hoy) {
        if ("HOY".equals(vigencia)) return hoy.toString();
        if ("SEMANA".equals(vigencia)) return hoy.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", hoy.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        return "P";
    }

    /** Días seguidos con actividad. Sigue «viva» si ayer hubo actividad aunque hoy todavía no. */
    public static int racha(Set<LocalDate> dias, LocalDate hoy) {
        LocalDate d = dias.contains(hoy) ? hoy : hoy.minusDays(1);
        int n = 0;
        while (dias.contains(d)) { n++; d = d.minusDays(1); }
        return n;
    }

    /** De todas las filas del usuario, deja la del ciclo actual de cada misión. */
    public static Map<Long, Fila> filasActuales(Collection<Fila> filas, Collection<Mision> misiones, LocalDate hoy) {
        Map<Long, Mision> porId = misiones.stream().collect(Collectors.toMap(Mision::id, m -> m, (a, b) -> a));
        Map<Long, Fila> r = new HashMap<>();
        for (Fila f : filas) {
            Mision m = porId.get(f.misionId());
            if (m != null && f.ciclo().equals(ciclo(m.vigencia(), hoy))) r.put(m.id(), f);
        }
        return r;
    }

    /** De todas las filas del usuario, deja la fila 'P' (el contador permanente) de cada misión que tiene una. */
    public static Map<Long, Fila> contadores(Collection<Fila> filas) {
        Map<Long, Fila> r = new HashMap<>();
        for (Fila f : filas) if ("P".equals(f.ciclo())) r.put(f.misionId(), f);
        return r;
    }

    // ------------------------------------------------------------------------------------------------ Estado y avance
    public static Fila contador(Mision m, Contexto c) { return c.contadorPorMision().get(m.id()); }

    /** Escalones logrados en una misión con escalera (0 si todavía no logró ninguno). */
    public static int nivelLogrado(Mision m, Contexto c) {
        Fila k = contador(m, c);
        return k == null ? 0 : k.nivel();
    }

    /** Una misión sin escalera está ganada cuando el usuario tiene su sello al nivel pedido; una con escalera, cuando la escalera terminó. */
    public static boolean ganada(Mision m, Contexto c) {
        if (m.esEscalera()) return m.metaDelNivel(nivelLogrado(m, c)) < 0;
        Integer n = c.nivelPorSello().get(m.sello().id());
        return n != null && n >= m.nivelSello();
    }

    /** La meta que se está persiguiendo ahora. */
    public static int metaActual(Mision m, Contexto c) {
        if (m.esEscalera() && !m.escaleraSobreVeces()) {
            int x = m.metaDelNivel(nivelLogrado(m, c));
            return x < 0 ? m.meta() : x;
        }
        return m.meta();
    }

    public static Estado estado(Mision m, Contexto c) {
        if ("OCULTA".equals(m.visibilidad())) return Estado.OCULTA;
        if (m.esRotativa()) {                                           // cada usuario tiene la suya: a los demás no se les muestra
            String a = c.asignaciones().get(m.id());
            if (a == null || "DESCARTADO".equals(a) || "CERRADO".equals(a)) return Estado.OCULTA;
            if ("COMPLETADO".equals(a)) return Estado.GANADA;
            if ("SIGUIENTE".equals(a)) return Estado.SIGUIENTE;
        }
        if (ganada(m, c)) return Estado.GANADA;
        if (m.hasta() != null && c.ahora().isAfter(m.hasta())) return Estado.EXPIRADA;
        if ("MISTERIOSA".equals(m.visibilidad()) && (m.sellosNecesarios() == null || c.totalSellos() < m.sellosNecesarios())) return Estado.MISTERIOSA;
        if (m.desde() != null && c.ahora().isBefore(m.desde())) return Estado.PROGRAMADA;
        if (m.prerrequisitoId() != null) {
            Mision p = c.misiones().get(m.prerrequisitoId());
            if (p != null && !ganada(p, c)) return Estado.BLOQUEADA;
        }
        if (m.escaleraSobreVeces()) {                                   // la del día ya cumplida hoy: vuelve mañana
            Fila f = c.filaPorMision().get(m.id());
            if (f != null && "CUMPLIDA".equals(f.estado())) return Estado.CUMPLIDA_CICLO;
        }
        return Estado.EN_CURSO;
    }

    public static int progreso(Mision m, Contexto c) {
        int meta = metaActual(m, c);
        if ("RACHA_DIAS".equals(m.metrica())) return Math.min(racha(c.diasActivos(), c.hoy()), meta);
        if (m.esEscalera() && !m.escaleraSobreVeces()) {
            Fila k = contador(m, c);
            return k == null ? 0 : Math.min(k.progreso(), meta);
        }
        Fila f = c.filaPorMision().get(m.id());
        return f == null ? 0 : Math.min(f.progreso(), meta);
    }

    public static List<Vista> vistas(Collection<Mision> misiones, Contexto c) {
        List<Vista> r = new ArrayList<>();
        for (Mision m : misiones) {
            Estado e = estado(m, c);
            if (e == Estado.OCULTA || e == Estado.GANADA || e == Estado.EXPIRADA) continue;
            int nivel = m.esEscalera() ? nivelLogrado(m, c) + 1 : 0;
            int veces = 0, metaVeces = 0;
            if (m.escaleraSobreVeces()) {
                Fila k = contador(m, c);
                veces = k == null ? 0 : k.veces();
                metaVeces = Math.max(0, m.metaDelNivel(nivelLogrado(m, c)));
            }
            r.add(new Vista(m, e, progreso(m, c), metaActual(m, c), nivel, veces, metaVeces));
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------------ «La más fácil de cumplir»
    private static int rangoVigencia(String v) {
        return switch (v) { case "HOY" -> 0; case "SEMANA" -> 1; case "RACHA" -> 2; case "EVENTO" -> 3; default -> 4; };
    }

    /** Menos esfuerzo que falta · si empatan, más avanzada · si empatan, la de vigencia más corta · luego el orden del admin. */
    public static final Comparator<Vista> MAS_FACIL = Comparator
            .comparingDouble(Vista::esfuerzoRestante)
            .thenComparing(Comparator.comparingDouble(Vista::porcentaje).reversed())
            .thenComparingInt((Vista v) -> rangoVigencia(v.mision().vigencia()))
            .thenComparingInt((Vista v) -> v.mision().orden())
            .thenComparingLong((Vista v) -> v.mision().id());

    public static List<Vista> enCurso(List<Vista> vistas) {
        return vistas.stream().filter(v -> v.estado() == Estado.EN_CURSO).sorted(MAS_FACIL).toList();
    }

    public static Optional<Vista> destacada(List<Vista> vistas) {
        return enCurso(vistas).stream().findFirst();
    }

    public static List<Vista> proximas(List<Vista> vistas) {
        return vistas.stream()
                .filter(v -> v.estado() == Estado.BLOQUEADA || v.estado() == Estado.PROGRAMADA || v.estado() == Estado.SIGUIENTE || v.estado() == Estado.MISTERIOSA)
                .sorted(Comparator.comparingInt((Vista v) -> switch (v.estado()) { case BLOQUEADA -> 0; case PROGRAMADA -> 1; case SIGUIENTE -> 2; default -> 3; })
                        .thenComparingInt((Vista v) -> v.mision().orden()))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------ ¿Esta actividad mueve esta misión?
    /** Sin saga ni elenco (compatibilidad). */
    public static boolean coincide(Mision m, ActividadEvento e, Set<Long> generos, Set<Long> directores) {
        return coincide(m, e, generos, directores, Set.of(), Set.of());
    }

    public static boolean coincide(Mision m, ActividadEvento e, Set<Long> generos, Set<Long> directores, Set<Long> sagas, Set<Long> elenco) {
        if (m.esDeSerie()) return coincideSerie(m, e);
        switch (m.metrica()) {
            case "VOTOS" -> { if (e.tipo() != ActividadEvento.Tipo.VOTO) return false; }
            case "COMENTARIOS" -> { if (e.tipo() != ActividadEvento.Tipo.COMENTARIO) return false; }
            case "TRIVIAS" -> { if (e.tipo() != ActividadEvento.Tipo.TRIVIA) return false; }
            case "PUBLICACIONES" -> { if (e.tipo() != ActividadEvento.Tipo.PUBLICACION) return false; }
            case "INTERVENCIONES" -> { if (e.tipo() != ActividadEvento.Tipo.INTERVENCION) return false; }
            case "CANJES" -> { if (e.tipo() != ActividadEvento.Tipo.CANJE) return false; }
            case "SORTEOS" -> { if (e.tipo() != ActividadEvento.Tipo.SORTEO) return false; }
            case "GUSTOS" -> { if (e.tipo() != ActividadEvento.Tipo.GUSTO) return false; }
            case "RECOMENDACIONES" -> { if (e.tipo() != ActividadEvento.Tipo.RECOMENDACION) return false; }
            case "RACHA_DIAS" -> { /* cualquier actividad cuenta */ }
            default -> { return false; }
        }
        if (m.tipoContenido() != null && !m.tipoContenido().equals(e.contenido().name())) return false;
        if (m.generoId() != null && !generos.contains(m.generoId())) return false;
        if (m.directorId() != null && !directores.contains(m.directorId())) return false;
        if (m.sagaId() != null && !sagas.contains(m.sagaId())) return false;
        if (m.actorId() != null && !elenco.contains(m.actorId())) return false;
        return true;
    }

    /** Los tres pasos de una misión de serie, y el número con que se cuenta cada uno (siempre 1, 2 o 3: son los «títulos distintos» de la misión). */
    public static Long pasoDeSerie(ActividadEvento.Tipo tipo) {
        return switch (tipo) { case VOTO -> 1L; case COMENTARIO -> 2L; case RECOMENDACION -> 3L; default -> null; };
    }

    /** Votar, comentar o recomendar ESA serie. Las películas y las demás series no cuentan. */
    private static boolean coincideSerie(Mision m, ActividadEvento e) {
        return pasoDeSerie(e.tipo()) != null && e.contenido() == ActividadEvento.Contenido.SERIE && m.serieId().equals(e.targetId());
    }

    /**
     * Lo que se anota para no contar dos veces lo mismo en una misión de «distintos»: normalmente el id de la película o serie; en una misión de
     * serie, el paso (votó, comentó, recomendó). null si el evento no tiene con qué contarse.
     */
    public static Long claveDistinta(Mision m, ActividadEvento e) {
        return m.esDeSerie() ? pasoDeSerie(e.tipo()) : e.targetId();
    }

    // ------------------------------------------------------------------------------------------------ JSON (mapas, para no depender de la versión de Jackson)
    private static Map<String, Object> selloMapa(Sello s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id()); m.put("nombre", s.nombre()); m.put("categoria", s.categoria());
        if (s.tieneImagen()) m.put("imagenUrl", s.imagenUrl());          // si no tiene, la aplicación lo dibuja con el ícono de su categoría
        if (s.tieneFondo()) m.put("fondoUrl", s.fondoUrl());             // la imagen ancha de la cabecera del detalle (solo las sagas, por ahora)
        return m;
    }

    /** La misión tal como la espera el front. conEstado = true para «próximas». */
    public static Map<String, Object> dto(Vista v, Contexto c, boolean conEstado) {
        Mision m = v.mision();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", m.id());
        if (v.estado() == Estado.MISTERIOSA) {                      // la silueta: no se filtra ni el nombre ni el sello
            d.put("titulo", "???"); d.put("categoria", m.categoria()); d.put("vigencia", m.vigencia());
            d.put("progreso", 0); d.put("meta", m.meta()); d.put("estado", "MISTERIOSA");
            if (m.sellosNecesarios() != null) {
                Map<String, Object> mis = new LinkedHashMap<>();
                mis.put("sellosActuales", c.totalSellos()); mis.put("sellosNecesarios", m.sellosNecesarios());
                d.put("misterio", mis);
            }
            return d;
        }
        d.put("titulo", m.titulo()); d.put("descripcion", m.descripcion()); d.put("categoria", m.categoria()); d.put("vigencia", m.vigencia());
        if (m.tipoContenido() != null) d.put("tipoContenido", m.tipoContenido());
        d.put("progreso", v.progreso()); d.put("meta", v.meta()); d.put("unidad", m.unidad());
        d.put("sello", selloMapa(m.sello()));
        if (m.esRotativa()) { d.put("familia", m.familia()); d.put("descartable", true); }      // lleva la ✕
        if (m.esEscalera()) {                                       // «Nv 3»: el escalón que se está persiguiendo
            d.put("escalera", true);
            d.put("nivel", v.nivel());
            if (m.escaleraSobreVeces()) { d.put("veces", v.veces()); d.put("metaVeces", v.metaVeces()); }
        }
        if (m.accionTexto() != null && !m.accionTexto().isBlank()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("texto", m.accionTexto()); if (m.accionDestino() != null) a.put("destino", m.accionDestino());
            d.put("accion", a);
        }
        if (conEstado) {
            d.put("estado", v.estado().name());
            if (v.estado() == Estado.BLOQUEADA) {
                Mision p = c.misiones().get(m.prerrequisitoId());
                d.put("desbloqueo", "Se desbloquea al ganar el sello «" + (p != null ? p.sello().nombre() : "?") + "».");
            }
            if (v.estado() == Estado.PROGRAMADA && m.desde() != null) d.put("comienza", m.desde().toLocalDate().toString());
            if (v.estado() == Estado.SIGUIENTE) d.put("desbloqueo", "Es tu próxima misión de " + etiquetaFamilia(m.familia()) + ": se activa cuando completes o descartes la actual.");
        }
        return d;
    }

    /** La pantalla de «Misión cumplida» (o «Subiste de nivel»). */
    public static Map<String, Object> cumplidaDto(Cumplida cu, Mision m, Optional<Vista> siguiente, Contexto c) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", cu.id()); d.put("titulo", m.titulo());
        if (m.descripcionCumplida() != null) d.put("descripcion", m.descripcionCumplida());
        d.put("sello", selloMapa(m.sello()));
        if (cu.nivel() > 1) d.put("nivel", cu.nivel());
        siguiente.ifPresent(s -> d.put("siguiente", dto(s, c, false)));
        return d;
    }

    /** El cartelito liviano: «Cumplida hoy» / «Cumplida esta semana». */
    public static Map<String, Object> avisoDto(Cumplida cu, Mision m, Contexto c) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", cu.id()); d.put("titulo", m.titulo());
        d.put("texto", "SEMANA".equals(m.vigencia()) ? "Cumplida esta semana" : "Cumplida hoy");
        Fila k = contador(m, c);
        if (k != null) d.put("veces", k.veces());
        return d;
    }

    public static Map<String, Object> selloGanadoDto(SelloGanado s, boolean conDescripcion) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", s.id()); d.put("nombre", s.nombre()); d.put("categoria", s.categoria());
        d.put("fecha", s.ganadoAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (conDescripcion && s.descripcion() != null) d.put("descripcion", s.descripcion());
        if (s.nivel() > 1) d.put("nivel", s.nivel());
        if (s.imagenUrl() != null && !s.imagenUrl().isBlank()) d.put("imagenUrl", s.imagenUrl());
        return d;
    }
}