package com.example.demo.domain.mision;

/** El SQL del catálogo automático de objetivos rotativos. Ver las tablas en 08_migracion_rotacion.sql. */
final class RotacionSql {
    private RotacionSql() {}

    /** Un candado que se suelta solo al terminar la transacción: así, aunque haya dos instancias del servidor, el catálogo se arma de a una. */
    static final String BLOQUEO = "SELECT pg_try_advisory_xact_lock(?)";

    static final String BUSCAR_OBJETIVO = """
            SELECT o.id, o.nombre, o.meta, o.mision_id, m.sello_id, o.activo, o.excluido
            FROM rotacion_objetivo o JOIN mision m ON m.id = o.mision_id
            WHERE o.familia = ? AND o.tmdb_id = ?
            """;

    static final String MISION_CON_TITULO = "SELECT count(*) FROM mision WHERE titulo = ?";

    static final String SELLO_USADO = "SELECT count(*) FROM sello s WHERE s.nombre = ? AND EXISTS (SELECT 1 FROM mision m WHERE m.sello_id = s.id)";

    static final String SELLO_LIBRE = "SELECT s.id FROM sello s WHERE s.nombre = ? AND NOT EXISTS (SELECT 1 FROM mision m WHERE m.sello_id = s.id) ORDER BY s.id LIMIT 1";

    static final String INSERTAR_SELLO = "INSERT INTO sello (nombre, descripcion, categoria, imagen_url, imagen_fondo_url) VALUES (?, ?, ?, ?, ?) RETURNING id";

    /** Completa las imágenes SOLO donde el sello no tiene: si el admin subió una propia, no se pisa. */
    static final String COMPLETAR_IMAGENES = "UPDATE sello SET imagen_url = COALESCE(imagen_url, ?), imagen_fondo_url = COALESCE(imagen_fondo_url, ?) WHERE id = ? AND ((imagen_url IS NULL AND ?::text IS NOT NULL) OR (imagen_fondo_url IS NULL AND ?::text IS NOT NULL))";

    static final String INSERTAR_MISION = """
            INSERT INTO mision (titulo, descripcion, descripcion_cumplida, categoria, metrica, meta, unidad, vigencia, tipo_contenido,
                                genero_id, saga_id, actor_id, director_id, sello_id, esfuerzo_por_paso, orden, activa, familia_rotacion, publicada_at, serie_id)
            VALUES (?, ?, ?, ?, 'VOTOS', ?, ?, 'PERMANENTE', ?, ?, ?, ?, ?, ?, 1.0, 500, TRUE, ?, NOW(), ?)
            RETURNING id
            """;

    static final String INSERTAR_OBJETIVO = "INSERT INTO rotacion_objetivo (familia, tmdb_id, nombre, meta, mision_id, popularidad) VALUES (?, ?, ?, ?, ?, ?)";

    static final String ACTUALIZAR_OBJETIVO = "UPDATE rotacion_objetivo SET nombre = ?, meta = ?, popularidad = ?, actualizado_at = NOW() WHERE id = ?";

    static final String TOCAR_OBJETIVO = "UPDATE rotacion_objetivo SET popularidad = ?, actualizado_at = NOW() WHERE id = ?";

    static final String ACTUALIZAR_MISION = "UPDATE mision SET meta = ?, descripcion = ?, descripcion_cumplida = ? WHERE id = ?";

    static final String ACTUALIZAR_SELLO = "UPDATE sello SET descripcion = ? WHERE id = ?";

    static final String PERSONAS_SIN_FOTO = """
            SELECT o.tmdb_id FROM rotacion_objetivo o JOIN mision m ON m.id = o.mision_id JOIN sello s ON s.id = m.sello_id
            WHERE o.familia = ? AND o.activo AND NOT o.excluido AND s.imagen_url IS NULL
            ORDER BY o.id
            """;

    static final String PONER_FOTO = """
            UPDATE sello SET imagen_url = ?
            WHERE imagen_url IS NULL AND id = (SELECT m.sello_id FROM rotacion_objetivo o JOIN mision m ON m.id = o.mision_id WHERE o.familia = ? AND o.tmdb_id = ?)
            """;

    static final String CONTAR_OBJETIVOS = "SELECT count(*) FROM rotacion_objetivo";

    /**
     * Series con actividad de los usuarios (votos, comentarios visibles y recomendaciones enviadas) que no están en el catálogo. Mismos criterios que
     * el conteo de «Reclamar», para que lo que se ofrece sea lo que de verdad se puede cumplir.
     */
    static final String SERIES_DE_LA_COMUNIDAD = """
            SELECT a.series_id, count(*) AS movimientos
            FROM (
                SELECT series_id FROM series_reviews WHERE vote_type IS NOT NULL AND COALESCE(active, TRUE)
                UNION ALL
                SELECT series_id FROM series_comments WHERE COALESCE(moderation_status, 'APPROVED') NOT IN ('REMOVED', 'REJECTED', 'HIDDEN_BY_USER', 'AUTO_HIDDEN')
                UNION ALL
                SELECT series_id FROM series_recommendations
            ) a
            WHERE a.series_id IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM rotacion_objetivo o WHERE o.familia = 'SERIE' AND o.tmdb_id = a.series_id)
            GROUP BY a.series_id
            ORDER BY movimientos DESC, a.series_id
            LIMIT ?
            """;
}