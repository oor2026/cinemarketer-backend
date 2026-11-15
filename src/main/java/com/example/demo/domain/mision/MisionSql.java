package com.example.demo.domain.mision;

/** Todo el SQL de las misiones (PostgreSQL). Cada constante se prueba contra una base real. */
final class MisionSql {
    private MisionSql() {}

    static final String MISIONES_ACTIVAS = """
            SELECT m.id, m.titulo, m.descripcion, m.descripcion_cumplida, m.categoria, m.metrica, m.meta, m.unidad, m.vigencia,
                   m.tipo_contenido, m.genero_id, m.director_id, m.saga_id, m.actor_id, m.distintas, m.sello_id, m.nivel_sello, m.esfuerzo_por_paso,
                   m.prerrequisito_mision_id, m.sellos_necesarios, m.visibilidad, m.desde, m.hasta, m.accion_texto, m.accion_destino,
                   m.orden, m.escalera, m.escalera_paso, m.escalera_sobre, m.familia_rotacion, m.audiencia,
                   s.nombre AS sello_nombre, s.categoria AS sello_categoria, s.descripcion AS sello_descripcion, s.imagen_url AS sello_imagen, s.imagen_fondo_url AS sello_fondo
            FROM mision m JOIN sello s ON s.id = m.sello_id
            WHERE m.activa AND s.activo
              AND (m.familia_rotacion IS NULL
                   OR m.id IN (SELECT o.mision_id FROM usuario_objetivo uo JOIN rotacion_objetivo o ON o.id = uo.objetivo_id WHERE uo.user_id = ?))
            ORDER BY m.orden, m.id
            """;

    static final String NIVELES_SELLOS = "SELECT sello_id, nivel FROM usuario_sello WHERE user_id = ?";

    static final String FILAS = """
            SELECT id, mision_id, ciclo, progreso, estado, nivel, veces
            FROM usuario_mision
            WHERE user_id = ? AND ciclo IN ('P', ?, ?)
            """;

    static final String DIAS_ACTIVOS = "SELECT fecha FROM actividad_dia WHERE user_id = ? AND fecha >= ?";

    static final String MARCAR_DIA = "INSERT INTO actividad_dia (user_id, fecha) VALUES (?, ?) ON CONFLICT DO NOTHING";

    static final String CREAR_FILA = """
            INSERT INTO usuario_mision (user_id, mision_id, ciclo, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, mision_id, ciclo) DO NOTHING
            """;

    static final String BLOQUEAR_FILA = """
            SELECT id, mision_id, ciclo, progreso, estado, nivel, veces
            FROM usuario_mision
            WHERE user_id = ? AND mision_id = ? AND ciclo = ?
            FOR UPDATE
            """;

    static final String CONTAR_TITULO = "INSERT INTO usuario_mision_titulo (usuario_mision_id, target_id) VALUES (?, ?) ON CONFLICT DO NOTHING";

    static final String ACTUALIZAR_PROGRESO = "UPDATE usuario_mision SET progreso = ?, updated_at = ? WHERE id = ? AND estado = 'EN_CURSO'";

    static final String ACTUALIZAR_CONTADOR = "UPDATE usuario_mision SET progreso = ?, nivel = ?, veces = ?, updated_at = ? WHERE id = ?";

    static final String CUMPLIR = """
            UPDATE usuario_mision SET progreso = ?, estado = 'CUMPLIDA', cumplida_at = ?, updated_at = ?
            WHERE id = ? AND estado = 'EN_CURSO'
            """;

    static final String OTORGAR_SELLO = """
            INSERT INTO usuario_sello (user_id, sello_id, nivel, ganado_at, mision_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, sello_id) DO UPDATE SET
                nivel     = GREATEST(usuario_sello.nivel, EXCLUDED.nivel),
                ganado_at = CASE WHEN EXCLUDED.nivel > usuario_sello.nivel THEN EXCLUDED.ganado_at ELSE usuario_sello.ganado_at END,
                mision_id = CASE WHEN EXCLUDED.nivel > usuario_sello.nivel THEN EXCLUDED.mision_id ELSE usuario_sello.mision_id END
            """;

    static final String CREAR_CELEBRACION = """
            INSERT INTO usuario_celebracion (user_id, mision_id, nivel, tipo, creada_at)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """;

    static final String CUMPLIDAS_SIN_VER = """
            SELECT id, mision_id, nivel, tipo, creada_at
            FROM usuario_celebracion
            WHERE user_id = ? AND visto_at IS NULL
            ORDER BY creada_at, id
            """;

    static final String MARCAR_VISTO = """
            UPDATE usuario_celebracion SET visto_at = ?
            WHERE id = ? AND user_id = ? AND visto_at IS NULL
            """;

    static final String SELLOS_GANADOS = """
            SELECT s.id, s.nombre, s.categoria, s.descripcion, us.nivel, us.ganado_at, s.imagen_url
            FROM usuario_sello us JOIN sello s ON s.id = us.sello_id
            WHERE us.user_id = ?
            ORDER BY us.ganado_at DESC, us.id DESC
            LIMIT ?
            """;

    static final String POR_DESCUBRIR = """
            SELECT COUNT(DISTINCT m.sello_id)
            FROM mision m
            WHERE m.activa AND m.visibilidad = 'MISTERIOSA'
              AND NOT EXISTS (SELECT 1 FROM usuario_sello us WHERE us.user_id = ? AND us.sello_id = m.sello_id AND us.nivel >= m.nivel_sello)
            """;

    // ---- rotación personal
    static final String ASIGNACIONES = """
            SELECT uo.id, uo.objetivo_id, o.mision_id, uo.familia, uo.estado, o.nombre
            FROM usuario_objetivo uo JOIN rotacion_objetivo o ON o.id = uo.objetivo_id
            WHERE uo.user_id = ?
            """;

    static final String CANDIDATOS = """
            SELECT o.id, o.mision_id
            FROM rotacion_objetivo o
                 JOIN mision m ON m.id = o.mision_id
                 JOIN sello s ON s.id = m.sello_id
            WHERE o.familia = ? AND o.activo AND NOT o.excluido AND m.activa AND s.activo
              AND NOT EXISTS (SELECT 1 FROM usuario_objetivo uo WHERE uo.user_id = ? AND uo.objetivo_id = o.id)
              AND NOT EXISTS (SELECT 1 FROM usuario_sello us WHERE us.user_id = ? AND us.sello_id = m.sello_id)
            """;

    static final String ASIGNAR = """
            INSERT INTO usuario_objetivo (user_id, familia, objetivo_id, estado, asignado_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    static final String CAMBIAR_ESTADO_OBJETIVO = """
            UPDATE usuario_objetivo
            SET estado = ?, cerrado_at = ?, asignado_at = COALESCE(?, asignado_at)
            WHERE user_id = ? AND objetivo_id = ?
            """;

    static final String QUITAR_ASIGNACION = "DELETE FROM usuario_objetivo WHERE user_id = ? AND objetivo_id = ? AND estado = 'DESCARTADO'";
}