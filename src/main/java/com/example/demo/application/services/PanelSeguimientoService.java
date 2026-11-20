package com.example.demo.application.services;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Panel de seguimiento de Creators: métricas personales de sus publicaciones en Comunidad.
 *
 * Criterios (iguales para todas las consultas):
 *  - Solo cuentan las publicaciones ACTIVAS del autor (visibles: no ocultas y aprobadas).
 *  - Se mide por FECHA DE LA INTERACCIÓN (no por la fecha de la publicación): un "Te banco" de ayer
 *    cuenta ayer aunque la publicación sea de hace un mes.
 *  - Las interacciones del propio autor (banco, punto, comentarios, vistas, respuestas) no cuentan.
 *  - Los días son días ARGENTINOS, sin importar la zona horaria del servidor. Las columnas created_at se
 *    guardan en la hora local de la JVM (UTC en producción, hora argentina en local), por eso cada consulta
 *    recibe un "desplazamiento" en segundos para llevarlas a hora argentina. Argentina no tiene horario de
 *    verano, así que el desplazamiento es constante.
 *  - Una reacción desactivada (active=false) no cuenta. Las reacciones se miden por su fecha de creación
 *    (si alguien la saca y la vuelve a poner, conserva la fecha original).
 *  - Alcance = personas LOGUEADAS distintas que vieron. Las visitas anónimas a la página pública suman a
 *    visualizaciones pero no a alcance.
 *  - Nunca se exponen puntajes de riesgo ni motivos internos de moderación: solo el estado.
 */
@Service
public class PanelSeguimientoService {

    private static final ZoneId ZONA_AR = ZoneId.of("America/Argentina/Buenos_Aires");
    public static final Set<Integer> PERIODOS_VALIDOS = Set.of(7, 28, 90);
    public static final int PERIODO_POR_DEFECTO = 28;

    // Fragmentos de SQL reutilizados. "p" = publicación del autor, ACTIVA.
    private static final String ACTIVA = "p.hidden = false AND p.moderation_status = 'APPROVED'";
    private static final String A_AR = "(CAST(:shift AS integer) * INTERVAL '1 second')";

    private final NamedParameterJdbcTemplate jdbc;

    public PanelSeguimientoService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==============================================
    // Período
    // ==============================================

    /** Rango de días argentinos [desdeDia, hastaDia] y su equivalente en hora de la JVM [desde, hasta). */
    public record Rango(LocalDate desdeDia, LocalDate hastaDia, LocalDateTime desde, LocalDateTime hasta, int shiftSegundos) {
    }

    public static int normalizarDias(Integer dias) {
        return dias != null && PERIODOS_VALIDOS.contains(dias) ? dias : PERIODO_POR_DEFECTO;
    }

    public Rango rango(int dias, boolean anterior) {
        LocalDate hoy = LocalDate.now(ZONA_AR);
        LocalDate hasta = anterior ? hoy.minusDays(dias) : hoy;
        LocalDate desde = hasta.minusDays(dias - 1L);
        ZoneId zonaServidor = ZoneId.systemDefault();
        LocalDateTime desdeJvm = desde.atStartOfDay(ZONA_AR).withZoneSameInstant(zonaServidor).toLocalDateTime();
        LocalDateTime hastaJvm = hasta.plusDays(1).atStartOfDay(ZONA_AR).withZoneSameInstant(zonaServidor).toLocalDateTime();
        Instant ahora = Instant.now();
        int shift = ZONA_AR.getRules().getOffset(ahora).getTotalSeconds()
                - zonaServidor.getRules().getOffset(ahora).getTotalSeconds();
        return new Rango(desde, hasta, desdeJvm, hastaJvm, shift);
    }

    private MapSqlParameterSource params(Long userId, Rango r) {
        return new MapSqlParameterSource()
                .addValue("uid", userId)
                .addValue("desde", Timestamp.valueOf(r.desde()))
                .addValue("hasta", Timestamp.valueOf(r.hasta()))
                .addValue("desdeDia", Date.valueOf(r.desdeDia()))
                .addValue("hastaDia", Date.valueOf(r.hastaDia()))
                .addValue("shift", r.shiftSegundos());
    }

    // ==============================================
    // Resumen (tarjetas)
    // ==============================================

    private static final String SQL_METRICAS = """
            SELECT
              (SELECT COUNT(*) FROM publications p
                WHERE p.user_id = :uid AND %1$s
                  AND p.created_at >= :desde AND p.created_at < :hasta) AS "publicaciones",
              (SELECT COUNT(*) FROM publication_reactions r JOIN publications p ON p.id = r.publication_id
                WHERE p.user_id = :uid AND %1$s AND r.active = true AND r.reaction_type = 'BANCO'
                  AND r.user_id <> :uid AND r.created_at >= :desde AND r.created_at < :hasta) AS "bancos",
              (SELECT COUNT(*) FROM publication_reactions r JOIN publications p ON p.id = r.publication_id
                WHERE p.user_id = :uid AND %1$s AND r.active = true AND r.reaction_type = 'PUNTO'
                  AND r.user_id <> :uid AND r.created_at >= :desde AND r.created_at < :hasta) AS "puntos",
              (SELECT COUNT(*) FROM publication_comments c JOIN publications p ON p.id = c.publication_id
                WHERE p.user_id = :uid AND %1$s AND c.hidden = false AND c.moderation_status = 'APPROVED'
                  AND c.user_id <> :uid AND c.created_at >= :desde AND c.created_at < :hasta) AS "comentarios",
              (SELECT COALESCE(SUM(v.views), 0) FROM publication_views v JOIN publications p ON p.id = v.publication_id
                WHERE p.user_id = :uid AND %1$s AND v.viewer_key <> CONCAT('u:', CAST(:uid AS text))
                  AND v.view_day BETWEEN :desdeDia AND :hastaDia) AS "visualizaciones",
              (SELECT COALESCE(SUM(v.views), 0) FROM publication_views v JOIN publications p ON p.id = v.publication_id
                WHERE p.user_id = :uid AND %1$s AND v.source = 'PUBLIC'
                  AND v.view_day BETWEEN :desdeDia AND :hastaDia) AS "visualizacionesPublicas",
              (SELECT COUNT(DISTINCT v.viewer_key) FROM publication_views v JOIN publications p ON p.id = v.publication_id
                WHERE p.user_id = :uid AND %1$s AND v.viewer_key LIKE 'u:%%'
                  AND v.viewer_key <> CONCAT('u:', CAST(:uid AS text))
                  AND v.view_day BETWEEN :desdeDia AND :hastaDia) AS "alcance",
              (SELECT COUNT(*) FROM publication_share_clicks s JOIN publications p ON p.id = s.publication_id
                WHERE p.user_id = :uid AND %1$s AND s.user_id <> :uid
                  AND s.created_at >= :desde AND s.created_at < :hasta) AS "compartidos",
              (SELECT COUNT(*) FROM user_follows f
                WHERE f.following_id = :uid AND f.status = 'ACCEPTED'
                  AND f.created_at >= :desde AND f.created_at < :hasta) AS "seguidoresNuevos",
              (SELECT COUNT(*) FROM profile_views pv
                WHERE pv.profile_user_id = :uid AND pv.viewer_id <> :uid
                  AND pv.view_day BETWEEN :desdeDia AND :hastaDia) AS "visitasPerfil",
              (SELECT COUNT(DISTINCT pv.viewer_id) FROM profile_views pv
                WHERE pv.profile_user_id = :uid AND pv.viewer_id <> :uid
                  AND pv.view_day BETWEEN :desdeDia AND :hastaDia) AS "visitantesPerfil",
              (SELECT COUNT(*) FROM publication_share_visits sv JOIN publications p ON p.id = sv.publication_id
                WHERE p.user_id = :uid AND %1$s
                  AND sv.visit_day BETWEEN :desdeDia AND :hastaDia) AS "visitasLink",
              (SELECT COUNT(*) FROM publication_share_signups ss JOIN publications p ON p.id = ss.publication_id
                WHERE p.user_id = :uid AND %1$s
                  AND ss.created_at >= :desde AND ss.created_at < :hasta) AS "registros",
              (SELECT COUNT(*) FROM publication_share_signups ss JOIN publications p ON p.id = ss.publication_id
                JOIN users u ON u.id = ss.new_user_id
                WHERE p.user_id = :uid AND %1$s AND u.email_verified = true
                  AND ss.created_at >= :desde AND ss.created_at < :hasta) AS "registrosVerificados"
            """.formatted(ACTIVA);

    private Map<String, Object> metricas(Long userId, Rango r) {
        return jdbc.queryForMap(SQL_METRICAS, params(userId, r));
    }

    public Map<String, Object> resumen(Long userId, int dias) {
        Rango actual = rango(dias, false);
        Rango anterior = rango(dias, true);

        Long seguidoresTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_follows WHERE following_id = :uid AND status = 'ACCEPTED'",
                new MapSqlParameterSource("uid", userId), Long.class);
        Long publicacionesActivas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM publications p WHERE p.user_id = :uid AND " + ACTIVA,
                new MapSqlParameterSource("uid", userId), Long.class);

        // Primer día (hora argentina) con visualizaciones registradas en toda la plataforma: las visualizaciones,
        // el alcance y los clics en compartir no existen antes de esa fecha, y el panel se lo aclara al Creator.
        String medicionDesde = jdbc.queryForObject(
                "SELECT CAST(CAST(MIN(created_at) + (CAST(:shift AS integer) * INTERVAL '1 second') AS date) AS text) FROM publication_views",
                new MapSqlParameterSource("shift", actual.shiftSegundos()), String.class);

        // Primer día (hora argentina) con visitas al perfil o visitas por link registradas (v1.5). Null si todavía no hay ninguna.
        String medicionNuevaDesde = jdbc.queryForObject(
                "SELECT CAST(CAST(MIN(t) + (CAST(:shift AS integer) * INTERVAL '1 second') AS date) AS text) FROM ("
                        + "SELECT MIN(created_at) AS t FROM profile_views "
                        + "UNION ALL SELECT MIN(created_at) FROM publication_share_visits) x",
                new MapSqlParameterSource("shift", actual.shiftSegundos()), String.class);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dias", dias);
        out.put("desde", actual.desdeDia().toString());
        out.put("hasta", actual.hastaDia().toString());
        out.put("medicionDesde", medicionDesde);
        out.put("medicionNuevaDesde", medicionNuevaDesde);
        out.put("actual", metricas(userId, actual));
        out.put("anterior", metricas(userId, anterior));
        out.put("seguidoresTotal", seguidoresTotal);
        out.put("publicacionesActivas", publicacionesActivas);
        return out;
    }

    // ==============================================
    // Evolución día por día
    // ==============================================

    private static final String SQL_TIMELINE = """
            WITH mis AS (SELECT p.id FROM publications p WHERE p.user_id = :uid AND %1$s %2$s),
            dias AS (
              SELECT CAST(g AS date) AS dia
              FROM generate_series(CAST(:desdeDia AS date), CAST(:hastaDia AS date), INTERVAL '1 day') g
            ),
            vis AS (
              SELECT v.view_day AS dia,
                     SUM(v.views) AS visualizaciones,
                     COUNT(DISTINCT CASE WHEN v.viewer_key LIKE 'u:%%' THEN v.viewer_key END) AS alcance
              FROM publication_views v
              WHERE v.publication_id IN (SELECT id FROM mis)
                AND v.viewer_key <> CONCAT('u:', CAST(:uid AS text))
                AND v.view_day BETWEEN :desdeDia AND :hastaDia
              GROUP BY v.view_day
            ),
            rea AS (
              SELECT CAST(r.created_at + %3$s AS date) AS dia,
                     COUNT(*) FILTER (WHERE r.reaction_type = 'BANCO') AS bancos,
                     COUNT(*) FILTER (WHERE r.reaction_type = 'PUNTO') AS puntos
              FROM publication_reactions r
              WHERE r.publication_id IN (SELECT id FROM mis) AND r.active = true AND r.user_id <> :uid
                AND r.created_at >= :desde AND r.created_at < :hasta
              GROUP BY 1
            ),
            com AS (
              SELECT CAST(c.created_at + %3$s AS date) AS dia, COUNT(*) AS comentarios
              FROM publication_comments c
              WHERE c.publication_id IN (SELECT id FROM mis) AND c.hidden = false
                AND c.moderation_status = 'APPROVED' AND c.user_id <> :uid
                AND c.created_at >= :desde AND c.created_at < :hasta
              GROUP BY 1
            ),
            cmp AS (
              SELECT CAST(s.created_at + %3$s AS date) AS dia, COUNT(*) AS compartidos
              FROM publication_share_clicks s
              WHERE s.publication_id IN (SELECT id FROM mis) AND s.user_id <> :uid
                AND s.created_at >= :desde AND s.created_at < :hasta
              GROUP BY 1
            )
            SELECT CAST(d.dia AS text) AS "dia",
                   COALESCE(vis.visualizaciones, 0) AS "visualizaciones",
                   COALESCE(vis.alcance, 0) AS "alcance",
                   COALESCE(rea.bancos, 0) AS "bancos",
                   COALESCE(rea.puntos, 0) AS "puntos",
                   COALESCE(com.comentarios, 0) AS "comentarios",
                   COALESCE(cmp.compartidos, 0) AS "compartidos"
            FROM dias d
            LEFT JOIN vis ON vis.dia = d.dia
            LEFT JOIN rea ON rea.dia = d.dia
            LEFT JOIN com ON com.dia = d.dia
            LEFT JOIN cmp ON cmp.dia = d.dia
            ORDER BY d.dia
            """;

    public List<Map<String, Object>> timeline(Long userId, int dias) {
        return jdbc.queryForList(SQL_TIMELINE.formatted(ACTIVA, "", A_AR), params(userId, rango(dias, false)));
    }

    /** Evolución de UNA publicación (ya verificada como del autor). */
    public List<Map<String, Object>> timelinePublicacion(Long userId, Long pubId, int dias) {
        MapSqlParameterSource ps = params(userId, rango(dias, false)).addValue("pid", pubId);
        return jdbc.queryForList(SQL_TIMELINE.formatted(ACTIVA, "AND p.id = :pid", A_AR), ps);
    }

    // ==============================================
    // Tabla de publicaciones
    // ==============================================

    private static final String SQL_PUBLICACIONES = """
            SELECT p.id AS "id",
                   p.title AS "titulo",
                   p.territory_group AS "territorio",
                   p.tone AS "tono",
                   to_char(p.created_at + %2$s, 'YYYY-MM-DD"T"HH24:MI:SS') AS "creadaEn",
                   CASE WHEN p.video_uid IS NOT NULL OR p.video_url IS NOT NULL THEN 'VIDEO'
                        WHEN p.image_urls IS NOT NULL AND cardinality(p.image_urls) > 0 THEN 'IMAGEN'
                        ELSE 'TEXTO' END AS "formato",
                   p.votacion_enabled AS "votacion", p.trivia_enabled AS "trivia", p.ranking_enabled AS "ranking",
                   p.countdown_enabled AS "countdown", p.movie_ficha_enabled AS "ficha", p.trailer_enabled AS "trailer",
                   COALESCE(v.visualizaciones, 0) AS "visualizaciones",
                   COALESCE(v.alcance, 0) AS "alcance",
                   COALESCE(r.bancos, 0) AS "bancos",
                   COALESCE(r.puntos, 0) AS "puntos",
                   COALESCE(c.comentarios, 0) AS "comentarios",
                   COALESCE(s.compartidos, 0) AS "compartidos"
            FROM publications p
            LEFT JOIN (
              SELECT pv.publication_id, SUM(pv.views) AS visualizaciones,
                     COUNT(DISTINCT CASE WHEN pv.viewer_key LIKE 'u:%%' THEN pv.viewer_key END) AS alcance
              FROM publication_views pv
              WHERE pv.view_day BETWEEN :desdeDia AND :hastaDia
                AND pv.viewer_key <> CONCAT('u:', CAST(:uid AS text))
              GROUP BY pv.publication_id
            ) v ON v.publication_id = p.id
            LEFT JOIN (
              SELECT rr.publication_id,
                     COUNT(*) FILTER (WHERE rr.reaction_type = 'BANCO') AS bancos,
                     COUNT(*) FILTER (WHERE rr.reaction_type = 'PUNTO') AS puntos
              FROM publication_reactions rr
              WHERE rr.active = true AND rr.user_id <> :uid AND rr.created_at >= :desde AND rr.created_at < :hasta
              GROUP BY rr.publication_id
            ) r ON r.publication_id = p.id
            LEFT JOIN (
              SELECT cc.publication_id, COUNT(*) AS comentarios
              FROM publication_comments cc
              WHERE cc.hidden = false AND cc.moderation_status = 'APPROVED' AND cc.user_id <> :uid
                AND cc.created_at >= :desde AND cc.created_at < :hasta
              GROUP BY cc.publication_id
            ) c ON c.publication_id = p.id
            LEFT JOIN (
              SELECT sc.publication_id, COUNT(*) AS compartidos
              FROM publication_share_clicks sc
              WHERE sc.user_id <> :uid AND sc.created_at >= :desde AND sc.created_at < :hasta
              GROUP BY sc.publication_id
            ) s ON s.publication_id = p.id
            WHERE p.user_id = :uid AND %1$s %3$s
            ORDER BY %4$s
            LIMIT %5$d
            """;

    // Orden permitido (lista blanca: el valor que llega del cliente nunca se pega en el SQL).
    private static String orden(String clave) {
        return switch (clave == null ? "" : clave) {
            case "visualizaciones" -> "\"visualizaciones\" DESC, p.created_at DESC";
            case "bancos" -> "\"bancos\" DESC, p.created_at DESC";
            case "comentarios" -> "\"comentarios\" DESC, p.created_at DESC";
            case "puntos" -> "\"puntos\" DESC, p.created_at DESC";
            default -> "p.created_at DESC";
        };
    }

    private List<Map<String, Object>> filasPublicaciones(Long userId, int dias, String filtro, String orden, int limite,
                                                         MapSqlParameterSource extra) {
        Rango r = rango(dias, false);
        MapSqlParameterSource ps = params(userId, r);
        if (extra != null) ps.addValues(extra.getValues());
        String sql = SQL_PUBLICACIONES.formatted(ACTIVA, A_AR, filtro, orden(orden), limite);
        List<Map<String, Object>> filas = jdbc.queryForList(sql, ps);
        for (Map<String, Object> f : filas) {
            List<String> herramientas = new ArrayList<>();
            if (Boolean.TRUE.equals(f.get("votacion"))) herramientas.add("Votación");
            if (Boolean.TRUE.equals(f.get("trivia"))) herramientas.add("Trivia");
            if (Boolean.TRUE.equals(f.get("ranking"))) herramientas.add("Ranking");
            if (Boolean.TRUE.equals(f.get("countdown"))) herramientas.add("Cuenta regresiva");
            if (Boolean.TRUE.equals(f.get("ficha"))) herramientas.add("Ficha");
            if (Boolean.TRUE.equals(f.get("trailer"))) herramientas.add("Tráiler");
            f.put("herramientas", herramientas);
            for (String k : List.of("votacion", "trivia", "ranking", "countdown", "ficha", "trailer")) f.remove(k);
        }
        return filas;
    }

    public List<Map<String, Object>> publicaciones(Long userId, int dias, String orden) {
        return filasPublicaciones(userId, dias, "", orden, 100, null);
    }

    // ==============================================
    // Detalle de una publicación
    // ==============================================

    /** true si la publicación existe y es del usuario (sin importar su estado). */
    public boolean esDelAutor(Long userId, Long pubId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM publications WHERE id = :pid AND user_id = :uid",
                new MapSqlParameterSource("pid", pubId).addValue("uid", userId), Long.class);
        return n != null && n > 0;
    }

    public Map<String, Object> detalle(Long userId, Long pubId, int dias) {
        List<Map<String, Object>> filas = filasPublicaciones(userId, dias, "AND p.id = :pid", "default", 1,
                new MapSqlParameterSource("pid", pubId));
        if (filas.isEmpty()) return null; // existe pero no está activa
        Map<String, Object> out = new LinkedHashMap<>(filas.get(0));
        out.put("dias", dias);
        out.put("timeline", timelinePublicacion(userId, pubId, dias));

        MapSqlParameterSource ps = new MapSqlParameterSource("pid", pubId).addValue("uid", userId);

        // Votación: se excluyen los votos del propio autor.
        List<Map<String, Object>> opcionesVot = jdbc.queryForList("""
                SELECT o.id AS "id", o.texto AS "texto", COUNT(v.id) AS "votos"
                FROM publication_votacion_opciones o
                LEFT JOIN publication_votacion_votos v ON v.opcion_id = o.id AND v.user_id <> :uid
                WHERE o.publication_id = :pid
                GROUP BY o.id, o.texto, o.orden
                ORDER BY o.orden
                """, ps);
        if (!opcionesVot.isEmpty()) {
            long total = opcionesVot.stream().mapToLong(o -> ((Number) o.get("votos")).longValue()).sum();
            for (Map<String, Object> o : opcionesVot) {
                long votos = ((Number) o.get("votos")).longValue();
                o.put("porcentaje", total == 0 ? 0 : Math.round(votos * 1000.0 / total) / 10.0);
            }
            Map<String, Object> vot = new LinkedHashMap<>();
            vot.put("totalVotos", total);
            vot.put("opciones", opcionesVot);
            out.put("votacionDetalle", vot);
        }

        // Trivia: el autor SÍ ve cuál es la correcta y el % de acierto (a diferencia de lo que ve el público).
        List<Map<String, Object>> opcionesTri = jdbc.queryForList("""
                SELECT o.id AS "id", o.texto AS "texto", o.es_correcta AS "correcta", COUNT(r.id) AS "respuestas"
                FROM publication_trivia_opciones o
                LEFT JOIN publication_trivia_respuestas r ON r.opcion_id = o.id AND r.user_id <> :uid
                WHERE o.publication_id = :pid
                GROUP BY o.id, o.texto, o.es_correcta, o.orden
                ORDER BY o.orden
                """, ps);
        if (!opcionesTri.isEmpty()) {
            long total = opcionesTri.stream().mapToLong(o -> ((Number) o.get("respuestas")).longValue()).sum();
            long aciertos = opcionesTri.stream().filter(o -> Boolean.TRUE.equals(o.get("correcta")))
                    .mapToLong(o -> ((Number) o.get("respuestas")).longValue()).sum();
            for (Map<String, Object> o : opcionesTri) {
                long n = ((Number) o.get("respuestas")).longValue();
                o.put("porcentaje", total == 0 ? 0 : Math.round(n * 1000.0 / total) / 10.0);
            }
            Map<String, Object> tri = new LinkedHashMap<>();
            tri.put("totalRespuestas", total);
            tri.put("porcentajeAcierto", total == 0 ? 0 : Math.round(aciertos * 1000.0 / total) / 10.0);
            tri.put("opciones", opcionesTri);
            out.put("triviaDetalle", tri);
        }
        return out;
    }

    // ==============================================
    // Estado de tus publicaciones (bloque secundario)
    // ==============================================

    // Cada publicación cae en UN solo estado (la rechazada manda sobre la oculta).
    private static final String ESTADO_CASE = """
            CASE WHEN p.moderation_status = 'REJECTED' THEN 'RECHAZADA'
                 WHEN p.hidden = true AND p.hidden_by = 'AUTHOR' THEN 'OCULTA_AUTOR'
                 WHEN p.hidden = true THEN 'OCULTA_MODERACION'
                 WHEN p.moderation_status = 'PENDING_REVIEW' THEN 'PENDIENTE'
                 WHEN p.moderation_status = 'PROCESSING' THEN 'PROCESANDO'
                 ELSE 'ACTIVA' END""";

    public Map<String, Object> estado(Long userId, int dias) {
        MapSqlParameterSource ps = params(userId, rango(dias, false));

        Map<String, Object> conteo = new LinkedHashMap<>();
        for (String k : List.of("ACTIVA", "PENDIENTE", "PROCESANDO", "RECHAZADA", "OCULTA_AUTOR", "OCULTA_MODERACION")) {
            conteo.put(k, 0L);
        }
        for (Map<String, Object> f : jdbc.queryForList(
                "SELECT " + ESTADO_CASE + " AS estado, COUNT(*) AS n FROM publications p WHERE p.user_id = :uid GROUP BY 1", ps)) {
            conteo.put((String) f.get("estado"), ((Number) f.get("n")).longValue());
        }

        // Publicaciones que NO están activas (sin motivos internos: solo el estado).
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT * FROM (
                  SELECT p.id AS "id", p.title AS "titulo",
                         to_char(p.created_at + %1$s, 'YYYY-MM-DD"T"HH24:MI:SS') AS "creadaEn",
                         %2$s AS "estado", p.created_at AS orden
                  FROM publications p WHERE p.user_id = :uid
                ) t WHERE "estado" <> 'ACTIVA' ORDER BY orden DESC LIMIT 30
                """.formatted(A_AR, ESTADO_CASE), ps);
        items.forEach(i -> i.remove("orden"));

        // Intentos que el filtro de palabras bloqueó antes de publicar (solo la categoría).
        Map<String, Object> bloqueos = new LinkedHashMap<>();
        List<Map<String, Object>> porCampo = jdbc.queryForList("""
                SELECT blocked_field AS "campo", COUNT(*) AS "cantidad"
                FROM moderation_block_events
                WHERE user_id = :uid AND created_at >= :desde AND created_at < :hasta
                GROUP BY blocked_field ORDER BY 2 DESC
                """, ps);
        long totalBloqueos = porCampo.stream().mapToLong(f -> ((Number) f.get("cantidad")).longValue()).sum();
        bloqueos.put("total", totalBloqueos);
        bloqueos.put("porCampo", porCampo);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dias", dias);
        out.put("conteo", conteo);
        out.put("items", items);
        out.put("bloqueosFiltro", bloqueos);
        return out;
    }

    // ==============================================
    // Qué te funciona mejor
    // ==============================================

    public Map<String, Object> insights(Long userId, int dias) {
        List<Map<String, Object>> filas = filasPublicaciones(userId, dias, "", "default", 500, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dias", dias);
        out.put("publicacionesAnalizadas", filas.size());
        out.put("porTerritorio", agrupar(filas, "territorio"));
        out.put("porTono", agrupar(filas, "tono"));
        out.put("porFormato", agrupar(filas, "formato"));
        return out;
    }

    /** Interacciones promedio por publicación (te banco + merecés un punto + comentarios), mejor primero. */
    private List<Map<String, Object>> agrupar(List<Map<String, Object>> filas, String campo) {
        Map<String, long[]> acum = new LinkedHashMap<>(); // [publicaciones, interacciones, visualizaciones]
        for (Map<String, Object> f : filas) {
            Object valor = f.get(campo);
            String clave = valor == null || valor.toString().isBlank() ? "Sin definir" : valor.toString();
            long[] a = acum.computeIfAbsent(clave, k -> new long[3]);
            a[0]++;
            a[1] += ((Number) f.get("bancos")).longValue() + ((Number) f.get("puntos")).longValue()
                    + ((Number) f.get("comentarios")).longValue();
            a[2] += ((Number) f.get("visualizaciones")).longValue();
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        acum.forEach((k, a) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("etiqueta", k);
            m.put("publicaciones", a[0]);
            m.put("interacciones", a[1]);
            m.put("visualizaciones", a[2]);
            m.put("promedioInteracciones", Math.round(a[1] * 10.0 / a[0]) / 10.0);
            lista.add(m);
        });
        lista.sort((x, y) -> Double.compare((Double) y.get("promedioInteracciones"), (Double) x.get("promedioInteracciones")));
        return lista;
    }
}