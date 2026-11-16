package com.example.demo.domain.mision;

/**
 * Todo el SQL de «Reclamar» (PostgreSQL). Cada constante se prueba contra una base real.
 *
 * Tablas del resto de la aplicación que se leen (solo lectura): reviews, series_reviews, comments, series_comments. Los nombres salen de
 * las entidades Review, SeriesReview, Comment y SeriesComment.
 */
final class HistorialSql {
    private HistorialSql() {}

    /**
     * Votos y comentarios del usuario. Un voto cuenta si tiene tipo de voto (como en vivo: solo los votos nuevos disparan el aviso).
     * Un comentario cuenta si todavía se ve: no eliminado, no rechazado, no ocultado por el usuario ni oculto automáticamente.
     */
    static final String EVENTOS = """
            SELECT 'VOTO' AS tipo, 'PELICULA' AS contenido, target_id AS id, created_at AS cuando
              FROM reviews
             WHERE user_id = ? AND review_type = 'MOVIE' AND vote_type IS NOT NULL AND COALESCE(active, TRUE)
            UNION ALL
            SELECT 'VOTO', 'SERIE', series_id, created_at
              FROM series_reviews
             WHERE user_id = ? AND vote_type IS NOT NULL AND COALESCE(active, TRUE)
            UNION ALL
            SELECT 'COMENTARIO', 'PELICULA', movie_id, created_at
              FROM comments
             WHERE user_id = ? AND COALESCE(moderation_status, 'APPROVED') NOT IN ('REMOVED', 'REJECTED', 'HIDDEN_BY_USER', 'AUTO_HIDDEN')
            UNION ALL
            SELECT 'COMENTARIO', 'SERIE', series_id, created_at
              FROM series_comments
             WHERE user_id = ? AND COALESCE(moderation_status, 'APPROVED') NOT IN ('REMOVED', 'REJECTED', 'HIDDEN_BY_USER', 'AUTO_HIDDEN')
            """;

    static final String REGISTROS = "SELECT mision_id, estado, base, calculado_at FROM usuario_historial WHERE user_id = ?";

    /** La fila 'P' (permanente) se crea con la primera actividad que el sistema cuenta en vivo en esa misión. */
    static final String PRIMER_AVANCE = "SELECT mision_id, created_at FROM usuario_mision WHERE user_id = ? AND ciclo = 'P'";

    static final String GUARDAR = """
            INSERT INTO usuario_historial (user_id, mision_id, estado, base, calculado_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, mision_id) DO UPDATE
               SET estado = EXCLUDED.estado, base = EXCLUDED.base, calculado_at = EXCLUDED.calculado_at
             WHERE usuario_historial.estado = 'IGNORADO'
            """;

    static final String MARCAR_RECLAMADO = """
            UPDATE usuario_historial SET estado = 'RECLAMADO', reclamado_at = ?
            WHERE user_id = ? AND mision_id = ? AND estado = 'PENDIENTE'
            """;

    static final String CONTAR_PENDIENTES = "SELECT COUNT(*) FROM usuario_historial WHERE user_id = ? AND estado = 'PENDIENTE'";

    private static final String COLUMNAS = """
            SELECT m.id, m.titulo, m.descripcion, m.descripcion_cumplida, m.categoria, m.metrica, m.meta, m.unidad, m.vigencia,
                   m.tipo_contenido, m.genero_id, m.director_id, m.saga_id, m.actor_id, m.distintas, m.sello_id, m.nivel_sello, m.esfuerzo_por_paso,
                   m.prerrequisito_mision_id, m.sellos_necesarios, m.visibilidad, m.desde, m.hasta, m.accion_texto, m.accion_destino,
                   m.orden, m.escalera, m.escalera_paso, m.escalera_sobre, m.familia_rotacion, m.audiencia,
                   s.nombre AS sello_nombre, s.categoria AS sello_categoria, s.descripcion AS sello_descripcion, s.imagen_url AS sello_imagen, s.imagen_fondo_url AS sello_fondo
            FROM mision m JOIN sello s ON s.id = m.sello_id
            """;

    /**
     * Rotativas del catálogo que el usuario nunca tuvo asignadas, de cuyo sello no es dueño, y que hay que evaluar (nunca evaluadas o IGNORADO
     * hace más de {@code repasoDesde}). Parámetros: user_id, user_id, user_id, repasoDesde.
     */
    static final String ROTATIVAS_PARA_EVALUAR = COLUMNAS + """
            WHERE m.activa AND s.activo AND m.familia_rotacion IS NOT NULL AND m.visibilidad <> 'OCULTA'
              AND m.id IN (SELECT o.mision_id FROM rotacion_objetivo o WHERE o.activo AND NOT o.excluido)
              AND NOT EXISTS (SELECT 1 FROM usuario_objetivo uo JOIN rotacion_objetivo o ON o.id = uo.objetivo_id
                               WHERE uo.user_id = ? AND o.mision_id = m.id)
              AND NOT EXISTS (SELECT 1 FROM usuario_sello us WHERE us.user_id = ? AND us.sello_id = m.sello_id)
              AND NOT EXISTS (SELECT 1 FROM usuario_historial h
                               WHERE h.user_id = ? AND h.mision_id = m.id AND (h.estado <> 'IGNORADO' OR h.calculado_at > ?))
            ORDER BY m.id
            """;

    /** Misiones activas por id (se arma con tantos «?» como ids). */
    static String misionesPorIds(int cantidad) {
        return COLUMNAS + " WHERE m.activa AND s.activo AND m.id IN (" + "?,".repeat(cantidad - 1) + "?) ORDER BY m.id";
    }

    static final String OBJETIVO_DE_MISION = "SELECT id FROM rotacion_objetivo WHERE mision_id = ?";
}
