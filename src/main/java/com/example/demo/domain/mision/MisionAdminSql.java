package com.example.demo.domain.mision;

/** El SQL del panel de admin. Ver las tablas en 01, 08, 10 y 11 de misiones. */
final class MisionAdminSql {
    private MisionAdminSql() {}

    /** Los dos primeros parámetros son el ciclo de hoy y el de esta semana (para contar solo el avance vigente de las misiones que se repiten). */
    static final String LISTAR = """
            SELECT m.id, m.titulo, m.descripcion, m.descripcion_cumplida, m.categoria, m.metrica, m.meta, m.unidad, m.vigencia, m.tipo_contenido,
                   m.genero_id, m.director_id, m.saga_id, m.actor_id, m.distintas, m.sello_id, m.nivel_sello, m.esfuerzo_por_paso, m.prerrequisito_mision_id,
                   m.sellos_necesarios, m.visibilidad, m.desde, m.hasta, m.accion_texto, m.accion_destino, m.orden, m.escalera, m.escalera_paso, m.escalera_sobre,
                   m.audiencia, m.origen, m.activa, m.publicada_at, m.created_at, m.creada_por, m.actualizada_at, m.actualizada_por,
                   s.nombre AS sello_nombre, s.descripcion AS sello_descripcion, s.imagen_url AS sello_imagen, s.categoria AS sello_categoria,
                   (SELECT count(DISTINCT um.user_id) FROM usuario_mision um
                     WHERE um.mision_id = m.id AND um.progreso > 0 AND um.estado = 'EN_CURSO' AND um.ciclo IN ('P', ?, ?)) AS en_curso,
                   (SELECT count(DISTINCT us.user_id) FROM usuario_sello us
                     WHERE us.sello_id = m.sello_id AND us.nivel >= CASE WHEN m.escalera IS NULL THEN m.nivel_sello ELSE 1 END) AS cumplieron,
                   (EXISTS (SELECT 1 FROM usuario_mision um WHERE um.mision_id = m.id)
                     OR EXISTS (SELECT 1 FROM usuario_sello us WHERE us.sello_id = m.sello_id)) AS con_progreso,
                   (SELECT count(*) FROM mision m2 WHERE m2.sello_id = m.sello_id) AS misiones_del_sello
            FROM mision m JOIN sello s ON s.id = m.sello_id
            """;

    static final String INSERTAR_SELLO = "INSERT INTO sello (nombre, descripcion, categoria, imagen_url) VALUES (?, ?, ?, ?) RETURNING id";

    static final String INSERTAR_MISION = """
            INSERT INTO mision (titulo, descripcion, descripcion_cumplida, categoria, metrica, meta, unidad, vigencia, tipo_contenido, genero_id, director_id, saga_id, actor_id,
                                distintas, sello_id, nivel_sello, esfuerzo_por_paso, prerrequisito_mision_id, sellos_necesarios, visibilidad, desde, hasta, accion_texto,
                                accion_destino, orden, escalera, escalera_paso, escalera_sobre, audiencia, activa, creada_por, actualizada_por, actualizada_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?, ?)
            RETURNING id
            """;

    static final String ACTUALIZAR_MISION = """
            UPDATE mision SET titulo = ?, descripcion = ?, descripcion_cumplida = ?, categoria = ?, metrica = ?, meta = ?, unidad = ?, vigencia = ?, tipo_contenido = ?,
                              genero_id = ?, director_id = ?, saga_id = ?, actor_id = ?, distintas = ?, sello_id = ?, nivel_sello = ?, esfuerzo_por_paso = ?,
                              prerrequisito_mision_id = ?, sellos_necesarios = ?, visibilidad = ?, desde = ?, hasta = ?, accion_texto = ?, accion_destino = ?, orden = ?,
                              escalera = ?, escalera_paso = ?, escalera_sobre = ?, audiencia = ?, actualizada_por = ?, actualizada_at = ?
            WHERE id = ? AND origen = 'MANUAL'
            """;

    /** El nombre, la descripción y la categoría que no vienen no se tocan; la imagen sí se guarda tal cual (vacía = se la sacó). */
    static final String RETOCAR_SELLO = """
            UPDATE sello SET nombre = COALESCE(?, nombre), descripcion = COALESCE(?, descripcion), categoria = COALESCE(?, categoria), imagen_url = ?
            WHERE id = ?
            """;

    static final String CAMBIAR_ACTIVA = """
            UPDATE mision SET activa = ?, publicada_at = CASE WHEN ? AND publicada_at IS NULL THEN ? ELSE publicada_at END, actualizada_por = ?, actualizada_at = ?
            WHERE id = ? AND origen = 'MANUAL'
            """;

    static final String USO_DE_MISION = """
            SELECT (SELECT count(*) FROM usuario_mision WHERE mision_id = ?)
                 + (SELECT count(*) FROM usuario_sello WHERE sello_id = (SELECT sello_id FROM mision WHERE id = ?))
                 + (SELECT count(*) FROM usuario_celebracion WHERE mision_id = ?)
            """;

    static final String DEPENDIENTES = "SELECT titulo FROM mision WHERE prerrequisito_mision_id = ? ORDER BY titulo";

    static final String SELLO_DE_MISION = "SELECT sello_id FROM mision WHERE id = ? AND origen = 'MANUAL'";

    static final String BORRAR_MISION = "DELETE FROM mision WHERE id = ? AND origen = 'MANUAL'";

    static final String BORRAR_SELLO_SUELTO = """
            DELETE FROM sello WHERE id = ?
              AND NOT EXISTS (SELECT 1 FROM mision WHERE sello_id = sello.id)
              AND NOT EXISTS (SELECT 1 FROM usuario_sello WHERE sello_id = sello.id)
            """;

    static final String SELLOS = """
            SELECT s.id, s.nombre, s.categoria, s.imagen_url,
                   (SELECT count(*) FROM mision m WHERE m.sello_id = s.id) AS misiones,
                   (SELECT count(DISTINCT us.user_id) FROM usuario_sello us WHERE us.sello_id = s.id) AS usuarios
            FROM sello s WHERE s.activo ORDER BY s.nombre
            """;

    static final String HISTORIAL = "SELECT id, usuario_id, accion, detalle, creada_at FROM mision_historial WHERE mision_id = ? ORDER BY creada_at DESC, id DESC LIMIT ?";

    static final String REGISTRAR_HISTORIAL = "INSERT INTO mision_historial (mision_id, titulo, usuario_id, accion, detalle) VALUES (?, ?, ?, ?, ?)";

    // ---- lo que necesita el validador
    static final String TITULO_EXISTE = "SELECT count(*) FROM mision WHERE lower(titulo) = lower(?) AND (?::bigint IS NULL OR id <> ?)";

    static final String SELLO_NOMBRE_EXISTE = "SELECT count(*) FROM sello WHERE lower(nombre) = lower(?) AND (?::bigint IS NULL OR id <> ?)";

    static final String SELLO_REF = """
            SELECT s.id, s.nombre, (SELECT count(*) FROM mision m WHERE m.sello_id = s.id) AS misiones,
                   EXISTS (SELECT 1 FROM usuario_sello us WHERE us.sello_id = s.id) AS ganado
            FROM sello s WHERE s.id = ?
            """;

    static final String MISION_REF = "SELECT id, titulo, activa, (origen = 'AUTO') AS automatica, prerrequisito_mision_id FROM mision WHERE id = ?";

    static final String ESFUERZO_MINIMO = """
            SELECT MIN(meta * esfuerzo_por_paso) FROM mision
            WHERE activa AND visibilidad = 'VISIBLE' AND (?::bigint IS NULL OR id <> ?) AND (? = 'TODOS' OR audiencia = 'TODOS' OR audiencia = ?)
            """;

    static final String SERIALIZAR_CREACIONES = "SELECT pg_advisory_xact_lock(7300302)";

    // ---- catálogo automático
    static final String OBJETIVOS = """
            SELECT o.id, o.familia, o.tmdb_id, o.nombre, o.meta, o.activo, o.excluido, m.activa AS mision_activa, o.popularidad, o.actualizado_at, o.mision_id,
                   (SELECT count(*) FROM usuario_objetivo uo WHERE uo.objetivo_id = o.id AND uo.estado IN ('ACTUAL', 'SIGUIENTE')) AS asignados,
                   (SELECT count(*) FROM usuario_objetivo uo WHERE uo.objetivo_id = o.id AND uo.estado = 'COMPLETADO') AS completados,
                   (SELECT count(*) FROM usuario_objetivo uo WHERE uo.objetivo_id = o.id AND uo.estado = 'DESCARTADO') AS descartados
            FROM rotacion_objetivo o JOIN mision m ON m.id = o.mision_id
            """;

    static final String RESUMEN_CATALOGO = """
            SELECT familia, count(*) AS total, count(*) FILTER (WHERE excluido) AS excluidos, max(actualizado_at) AS ultima
            FROM rotacion_objetivo GROUP BY familia ORDER BY familia
            """;

    static final String OBJETIVO_MISION = "SELECT mision_id, nombre FROM rotacion_objetivo WHERE id = ?";

    static final String EXCLUIR_OBJETIVO = "UPDATE rotacion_objetivo SET excluido = ?, actualizado_at = ? WHERE id = ?";

    static final String ACTIVAR_MISION_AUTO = "UPDATE mision SET activa = ?, actualizada_por = ?, actualizada_at = ? WHERE id = ? AND origen = 'AUTO'";
}
