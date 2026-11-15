package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionAdminValidador.MisionRef;
import com.example.demo.domain.mision.MisionAdminValidador.Normalizada;
import com.example.demo.domain.mision.MisionAdminValidador.SelloRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** El panel de admin contra PostgreSQL. Las operaciones que escriben se llaman desde MisionAdminService, que ya abre la transacción. */
@Repository
public class MisionAdminRepositorio implements MisionAdminDatos {

    private final JdbcTemplate jdbc;

    public MisionAdminRepositorio(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ------------------------------------------------------------------------------------------------ lectura
    private static LocalDateTime fecha(Timestamp t) { return t == null ? null : t.toLocalDateTime(); }
    private static Long largo(ResultSet rs, String c) throws SQLException { long v = rs.getLong(c); return rs.wasNull() ? null : v; }
    private static Integer entero(ResultSet rs, String c) throws SQLException { int v = rs.getInt(c); return rs.wasNull() ? null : v; }
    private static Timestamp ts(LocalDateTime d) { return d == null ? null : Timestamp.valueOf(d); }

    private static final RowMapper<Fila> FILA = (rs, n) -> new Fila(rs.getLong("id"), rs.getString("titulo"), rs.getString("descripcion"), rs.getString("descripcion_cumplida"),
            rs.getString("categoria"), rs.getString("metrica"), rs.getInt("meta"), rs.getString("unidad"), rs.getString("vigencia"), rs.getString("tipo_contenido"),
            largo(rs, "genero_id"), largo(rs, "director_id"), largo(rs, "saga_id"), largo(rs, "actor_id"), rs.getBoolean("distintas"), rs.getLong("sello_id"), rs.getInt("nivel_sello"),
            rs.getDouble("esfuerzo_por_paso"), largo(rs, "prerrequisito_mision_id"), entero(rs, "sellos_necesarios"), rs.getString("visibilidad"), fecha(rs.getTimestamp("desde")),
            fecha(rs.getTimestamp("hasta")), rs.getString("accion_texto"), rs.getString("accion_destino"), rs.getInt("orden"), rs.getString("escalera"), entero(rs, "escalera_paso"),
            rs.getString("escalera_sobre"), rs.getString("audiencia"), rs.getString("origen"), rs.getBoolean("activa"), fecha(rs.getTimestamp("publicada_at")),
            fecha(rs.getTimestamp("created_at")), largo(rs, "creada_por"), fecha(rs.getTimestamp("actualizada_at")), largo(rs, "actualizada_por"),
            rs.getString("sello_nombre"), rs.getString("sello_descripcion"), rs.getString("sello_imagen"), rs.getString("sello_categoria"),
            rs.getInt("en_curso"), rs.getInt("cumplieron"), rs.getBoolean("con_progreso"), rs.getInt("misiones_del_sello"));

    @Override
    public List<Fila> listar(Filtros f, String cicloDia, String cicloSemana, LocalDateTime ahora) {
        StringBuilder sql = new StringBuilder(MisionAdminSql.LISTAR).append(" WHERE TRUE");
        List<Object> p = new ArrayList<>(List.of(cicloDia, cicloSemana));
        if (f != null) {
            if (hay(f.q())) { sql.append(" AND (m.titulo ILIKE ? OR s.nombre ILIKE ?)"); String like = "%" + f.q().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; p.add(like); p.add(like); }
            if (hay(f.origen())) { sql.append(" AND m.origen = ?"); p.add(f.origen()); }
            if (hay(f.categoria())) { sql.append(" AND m.categoria = ?"); p.add(f.categoria()); }
            if (hay(f.metrica())) { sql.append(" AND m.metrica = ?"); p.add(f.metrica()); }
            if (hay(f.vigencia())) { sql.append(" AND m.vigencia = ?"); p.add(f.vigencia()); }
            if (hay(f.estado())) {
                Timestamp a = Timestamp.valueOf(ahora);
                switch (f.estado()) {
                    case "BORRADOR" -> sql.append(" AND NOT m.activa AND m.publicada_at IS NULL");
                    case "PAUSADA" -> sql.append(" AND NOT m.activa AND m.publicada_at IS NOT NULL");
                    case "ACTIVA" -> { sql.append(" AND m.activa AND (m.hasta IS NULL OR m.hasta >= ?) AND (m.desde IS NULL OR m.desde <= ?)"); p.add(a); p.add(a); }
                    case "PROGRAMADA" -> { sql.append(" AND m.activa AND m.desde > ?"); p.add(a); }
                    case "VENCIDA" -> { sql.append(" AND m.activa AND m.hasta < ?"); p.add(a); }
                    default -> sql.append(" AND FALSE");                       // un estado que no existe no devuelve nada
                }
            }
        }
        sql.append(" ORDER BY m.origen DESC, m.activa DESC, m.orden, m.id");        // primero las manuales (MANUAL > AUTO al ordenar de forma descendente)
        return jdbc.query(sql.toString(), FILA, p.toArray());
    }

    private static boolean hay(String s) { return s != null && !s.isBlank(); }

    @Override
    public Optional<Fila> obtener(long id, String cicloDia, String cicloSemana) {
        List<Fila> r = jdbc.query(MisionAdminSql.LISTAR + " WHERE m.id = ?", FILA, cicloDia, cicloSemana, id);
        return r.stream().findFirst();
    }

    // ------------------------------------------------------------------------------------------------ escritura
    @Override
    public long crear(Normalizada n, long adminId, LocalDateTime ahora) {
        long selloId = n.selloNuevo()
                ? jdbc.queryForObject(MisionAdminSql.INSERTAR_SELLO, Long.class, n.selloNombre(), n.selloDescripcion(), n.selloCategoria(), n.selloImagenUrl())
                : n.selloId();
        Long id = jdbc.queryForObject(MisionAdminSql.INSERTAR_MISION, Long.class, n.titulo(), n.descripcion(), n.descripcionCumplida(), n.categoria(), n.metrica(), n.meta(),
                n.unidad(), n.vigencia(), n.tipoContenido(), n.generoId(), n.directorId(), n.sagaId(), n.actorId(), n.distintas(), selloId, n.nivelSello(), n.esfuerzo(),
                n.prerrequisitoId(), n.sellosNecesarios(), n.visibilidad(), ts(n.desde()), ts(n.hasta()), n.accionTexto(), n.accionDestino(), n.orden(), n.escaleraCsv(),
                n.escaleraPaso(), n.escaleraSobre(), n.audiencia(), adminId, adminId, ts(ahora));
        return id == null ? 0 : id;
    }

    @Override
    public void actualizar(long id, Normalizada n, boolean retocarSello, long adminId, LocalDateTime ahora) {
        Long selloActual = jdbc.query(MisionAdminSql.SELLO_DE_MISION, (rs, i) -> rs.getLong(1), id).stream().findFirst().orElse(null);
        if (selloActual == null) return;                                                    // no existe o es automática
        long selloId = n.selloId() != null ? n.selloId() : selloActual;
        jdbc.update(MisionAdminSql.ACTUALIZAR_MISION, n.titulo(), n.descripcion(), n.descripcionCumplida(), n.categoria(), n.metrica(), n.meta(), n.unidad(), n.vigencia(), n.tipoContenido(),
                n.generoId(), n.directorId(), n.sagaId(), n.actorId(), n.distintas(), selloId, n.nivelSello(), n.esfuerzo(), n.prerrequisitoId(), n.sellosNecesarios(), n.visibilidad(),
                ts(n.desde()), ts(n.hasta()), n.accionTexto(), n.accionDestino(), n.orden(), n.escaleraCsv(), n.escaleraPaso(), n.escaleraSobre(), n.audiencia(), adminId, ts(ahora), id);
        if (retocarSello) jdbc.update(MisionAdminSql.RETOCAR_SELLO, n.selloNombre(), n.selloDescripcion(), n.selloCategoria(), n.selloImagenUrl(), selloId);
    }

    @Override
    public void cambiarActiva(long id, boolean activa, long adminId, LocalDateTime ahora) {
        jdbc.update(MisionAdminSql.CAMBIAR_ACTIVA, activa, activa, ts(ahora), adminId, ts(ahora), id);
    }

    @Override
    public boolean eliminar(long id) {
        Integer uso = jdbc.queryForObject(MisionAdminSql.USO_DE_MISION, Integer.class, id, id, id);
        if (uso != null && uso > 0) return false;
        Long sello = jdbc.query(MisionAdminSql.SELLO_DE_MISION, (rs, i) -> rs.getLong(1), id).stream().findFirst().orElse(null);
        if (sello == null) return false;
        if (jdbc.update(MisionAdminSql.BORRAR_MISION, id) == 0) return false;
        jdbc.update(MisionAdminSql.BORRAR_SELLO_SUELTO, sello);                              // el sello se va solo si ninguna otra misión lo usa ni nadie lo ganó
        return true;
    }

    @Override public List<String> dependientes(long id) { return jdbc.query(MisionAdminSql.DEPENDIENTES, (rs, i) -> rs.getString(1), id); }

    @Override
    public List<SelloItem> sellos() {
        return jdbc.query(MisionAdminSql.SELLOS, (rs, i) -> new SelloItem(rs.getLong("id"), rs.getString("nombre"), rs.getString("categoria"), rs.getString("imagen_url"), rs.getInt("misiones"), rs.getInt("usuarios")));
    }

    @Override
    public List<HistorialItem> historial(long misionId, int limite) {
        return jdbc.query(MisionAdminSql.HISTORIAL, (rs, i) -> new HistorialItem(rs.getLong("id"), largo(rs, "usuario_id"), rs.getString("accion"), rs.getString("detalle"), fecha(rs.getTimestamp("creada_at"))), misionId, limite);
    }

    @Override
    public void registrarHistorial(Long misionId, String titulo, Long adminId, String accion, String detalle) {
        jdbc.update(MisionAdminSql.REGISTRAR_HISTORIAL, misionId == null ? 0 : misionId, titulo, adminId, accion, detalle);
    }

    // ------------------------------------------------------------------------------------------------ para el validador
    private int contar(String sql, String nombre, Long excluir) { Integer n = jdbc.queryForObject(sql, Integer.class, nombre, excluir, excluir); return n == null ? 0 : n; }

    @Override public boolean tituloExiste(String titulo, Long excluir) { return contar(MisionAdminSql.TITULO_EXISTE, titulo, excluir) > 0; }

    @Override public boolean selloNombreExiste(String nombre, Long excluir) { return contar(MisionAdminSql.SELLO_NOMBRE_EXISTE, nombre, excluir) > 0; }

    @Override
    public Optional<SelloRef> sello(long id) {
        return jdbc.query(MisionAdminSql.SELLO_REF, (rs, i) -> new SelloRef(rs.getLong("id"), rs.getString("nombre"), rs.getInt("misiones"), rs.getBoolean("ganado")), id).stream().findFirst();
    }

    @Override
    public Optional<MisionRef> mision(long id) {
        return jdbc.query(MisionAdminSql.MISION_REF, (rs, i) -> new MisionRef(rs.getLong("id"), rs.getString("titulo"), rs.getBoolean("activa"), rs.getBoolean("automatica"), largo(rs, "prerrequisito_mision_id")), id).stream().findFirst();
    }

    @Override
    public Double esfuerzoMinimoActivas(Long excluir, String audiencia) {
        return jdbc.queryForObject(MisionAdminSql.ESFUERZO_MINIMO, Double.class, excluir, excluir, audiencia, audiencia);
    }

    @Override public void serializarCreaciones() { jdbc.queryForObject(MisionAdminSql.SERIALIZAR_CREACIONES, Object.class); }

    // ------------------------------------------------------------------------------------------------ catálogo automático
    @Override
    public List<ObjetivoFila> objetivos(String familia, String q, String estado) {
        StringBuilder sql = new StringBuilder(MisionAdminSql.OBJETIVOS).append(" WHERE TRUE");
        List<Object> p = new ArrayList<>();
        if (hay(familia)) { sql.append(" AND o.familia = ?"); p.add(familia); }
        if (hay(q)) { sql.append(" AND o.nombre ILIKE ?"); p.add("%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"); }
        if (hay(estado)) {
            switch (estado) {
                case "EXCLUIDO" -> sql.append(" AND o.excluido");
                case "ACTIVO" -> sql.append(" AND NOT o.excluido AND m.activa");
                case "RETIRADO" -> sql.append(" AND o.excluido AND NOT m.activa");
                default -> sql.append(" AND FALSE");
            }
        }
        sql.append(" ORDER BY o.familia, o.nombre");
        return jdbc.query(sql.toString(), (rs, i) -> {
            double pop = rs.getDouble("popularidad");
            return new ObjetivoFila(rs.getLong("id"), rs.getString("familia"), rs.getLong("tmdb_id"), rs.getString("nombre"), rs.getInt("meta"), rs.getBoolean("activo"), rs.getBoolean("excluido"),
                    rs.getBoolean("mision_activa"), rs.wasNull() ? null : pop, fecha(rs.getTimestamp("actualizado_at")), rs.getLong("mision_id"), rs.getInt("asignados"), rs.getInt("completados"), rs.getInt("descartados"));
        }, p.toArray());
    }

    @Override
    public List<ResumenFamilia> resumenCatalogo() {
        return jdbc.query(MisionAdminSql.RESUMEN_CATALOGO, (rs, i) -> new ResumenFamilia(rs.getString("familia"), rs.getInt("total"), rs.getInt("excluidos"), fecha(rs.getTimestamp("ultima"))));
    }

    @Override
    public boolean cambiarExclusion(long objetivoId, boolean excluido, boolean retirar, long adminId, LocalDateTime ahora) {
        List<Object[]> o = jdbc.query(MisionAdminSql.OBJETIVO_MISION, (rs, i) -> new Object[]{rs.getLong("mision_id"), rs.getString("nombre")}, objetivoId);
        if (o.isEmpty()) return false;
        long misionId = (Long) o.get(0)[0]; String nombre = (String) o.get(0)[1];
        jdbc.update(MisionAdminSql.EXCLUIR_OBJETIVO, excluido, ts(ahora), objetivoId);
        // Excluir con «retirar» le saca la misión a quien ya la tiene (el motor la cierra y pasa la siguiente). Restaurar siempre la vuelve a activar.
        if (!excluido || retirar) jdbc.update(MisionAdminSql.ACTIVAR_MISION_AUTO, !excluido, adminId, ts(ahora), misionId);
        jdbc.update(MisionAdminSql.REGISTRAR_HISTORIAL, misionId, nombre, adminId, "EDITAR",
                excluido ? "Catálogo automático: excluida" + (retirar ? " y retirada a quienes la tenían asignada" : " (quienes ya la tienen la conservan)") : "Catálogo automático: restaurada");
        return true;
    }
}
