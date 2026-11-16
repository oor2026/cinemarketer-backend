package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionLogica.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** MisionDatos contra PostgreSQL (JdbcTemplate). Las tablas se crean con 01_ddl_misiones.sql. */
@Repository
public class MisionRepositorio implements MisionDatos {

    private final JdbcTemplate jdbc;

    public MisionRepositorio(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static LocalDateTime fecha(Timestamp t) { return t == null ? null : t.toLocalDateTime(); }
    private static Long largo(ResultSet rs, String col) throws SQLException { long v = rs.getLong(col); return rs.wasNull() ? null : v; }
    private static Integer entero(ResultSet rs, String col) throws SQLException { int v = rs.getInt(col); return rs.wasNull() ? null : v; }

    static final RowMapper<Mision> MISION = (rs, n) -> new Mision(
            rs.getLong("id"), rs.getString("titulo"), rs.getString("descripcion"), rs.getString("descripcion_cumplida"),
            rs.getString("categoria"), rs.getString("metrica"), rs.getInt("meta"), rs.getString("unidad"), rs.getString("vigencia"),
            rs.getString("tipo_contenido"), largo(rs, "genero_id"), largo(rs, "director_id"), largo(rs, "saga_id"), largo(rs, "actor_id"), rs.getBoolean("distintas"),
            new Sello(rs.getLong("sello_id"), rs.getString("sello_nombre"), rs.getString("sello_categoria"), rs.getString("sello_descripcion"), rs.getString("sello_imagen"), rs.getString("sello_fondo")),
            rs.getInt("nivel_sello"), rs.getDouble("esfuerzo_por_paso"), largo(rs, "prerrequisito_mision_id"), entero(rs, "sellos_necesarios"),
            rs.getString("visibilidad"), fecha(rs.getTimestamp("desde")), fecha(rs.getTimestamp("hasta")),
            rs.getString("accion_texto"), rs.getString("accion_destino"), rs.getInt("orden"),
            MisionLogica.parseEscalera(rs.getString("escalera")), entero(rs, "escalera_paso"), rs.getString("escalera_sobre"), rs.getString("familia_rotacion"), rs.getString("audiencia"), largo(rs, "serie_id"));

    private static final RowMapper<Fila> FILA = (rs, n) -> new Fila(rs.getLong("id"), rs.getLong("mision_id"), rs.getString("ciclo"), rs.getInt("progreso"), rs.getString("estado"), rs.getInt("nivel"), rs.getInt("veces"));

    @Override public List<Mision> misionesActivas(long userId) { return jdbc.query(MisionSql.MISIONES_ACTIVAS, MISION, userId); }

    @Override public Map<Long, Integer> nivelesDeSellos(long userId) {
        Map<Long, Integer> r = new HashMap<>();
        jdbc.query(MisionSql.NIVELES_SELLOS, (rs, n) -> r.put(rs.getLong("sello_id"), rs.getInt("nivel")), userId);
        return r;
    }

    @Override public List<Fila> filas(long userId, String cicloDia, String cicloSemana) { return jdbc.query(MisionSql.FILAS, FILA, userId, cicloDia, cicloSemana); }

    @Override public Set<LocalDate> diasActivos(long userId, LocalDate desde) {
        return new HashSet<>(jdbc.query(MisionSql.DIAS_ACTIVOS, (rs, n) -> rs.getDate("fecha").toLocalDate(), userId, java.sql.Date.valueOf(desde)));
    }

    @Override public void marcarDia(long userId, LocalDate dia) { jdbc.update(MisionSql.MARCAR_DIA, userId, java.sql.Date.valueOf(dia)); }

    @Override public Fila filaParaActualizar(long userId, long misionId, String ciclo, LocalDateTime ahora) {
        Timestamp t = Timestamp.valueOf(ahora);
        jdbc.update(MisionSql.CREAR_FILA, userId, misionId, ciclo, t, t);
        return jdbc.queryForObject(MisionSql.BLOQUEAR_FILA, FILA, userId, misionId, ciclo);
    }

    @Override public boolean contarTitulo(long filaId, long targetId) { return jdbc.update(MisionSql.CONTAR_TITULO, filaId, targetId) > 0; }

    @Override public void actualizarProgreso(long filaId, int progreso, LocalDateTime ahora) { jdbc.update(MisionSql.ACTUALIZAR_PROGRESO, progreso, Timestamp.valueOf(ahora), filaId); }

    @Override public void actualizarContador(long filaId, int progreso, int nivel, int veces, LocalDateTime ahora) {
        jdbc.update(MisionSql.ACTUALIZAR_CONTADOR, progreso, nivel, veces, Timestamp.valueOf(ahora), filaId);
    }

    @Override public boolean cumplir(long filaId, int progreso, LocalDateTime ahora) {
        Timestamp t = Timestamp.valueOf(ahora);
        return jdbc.update(MisionSql.CUMPLIR, progreso, t, t, filaId) > 0;
    }

    @Override public void otorgarSello(long userId, long selloId, int nivel, long misionId, LocalDateTime ahora) {
        jdbc.update(MisionSql.OTORGAR_SELLO, userId, selloId, nivel, Timestamp.valueOf(ahora), misionId);
    }

    @Override public long crearCelebracion(long userId, long misionId, int nivel, String tipo, LocalDateTime ahora) {
        Long id = jdbc.queryForObject(MisionSql.CREAR_CELEBRACION, Long.class, userId, misionId, nivel, tipo, Timestamp.valueOf(ahora));
        return id == null ? 0 : id;
    }

    @Override public List<Cumplida> cumplidasSinVer(long userId) {
        return jdbc.query(MisionSql.CUMPLIDAS_SIN_VER, (rs, n) -> new Cumplida(rs.getLong("id"), rs.getLong("mision_id"), rs.getInt("nivel"), rs.getString("tipo"), fecha(rs.getTimestamp("creada_at"))), userId);
    }

    @Override public boolean marcarVisto(long userId, long cumplidaId, LocalDateTime ahora) { return jdbc.update(MisionSql.MARCAR_VISTO, Timestamp.valueOf(ahora), cumplidaId, userId) > 0; }

    @Override public List<SelloGanado> sellosGanados(long userId, int limite) {
        return jdbc.query(MisionSql.SELLOS_GANADOS, (rs, n) -> new SelloGanado(rs.getLong("id"), rs.getString("nombre"), rs.getString("categoria"),
                rs.getString("descripcion"), rs.getInt("nivel"), fecha(rs.getTimestamp("ganado_at")), rs.getString("imagen_url")), userId, limite);
    }

    @Override public int porDescubrir(long userId) { Integer n = jdbc.queryForObject(MisionSql.POR_DESCUBRIR, Integer.class, userId); return n == null ? 0 : n; }

    // ---- rotación personal
    private static final RowMapper<Asignacion> ASIGNACION = (rs, n) -> new Asignacion(rs.getLong("id"), rs.getLong("objetivo_id"), rs.getLong("mision_id"), rs.getString("familia"), rs.getString("estado"), rs.getString("nombre"));

    @Override public List<Asignacion> asignaciones(long userId) { return jdbc.query(MisionSql.ASIGNACIONES, ASIGNACION, userId); }

    @Override public List<Candidato> candidatos(long userId, String familia) {
        return jdbc.query(MisionSql.CANDIDATOS, (rs, n) -> new Candidato(rs.getLong("id"), rs.getLong("mision_id")), familia, userId, userId);
    }

    @Override public void asignar(long userId, String familia, long objetivoId, String estado, LocalDateTime ahora) {
        jdbc.update(MisionSql.ASIGNAR, userId, familia, objetivoId, estado, Timestamp.valueOf(ahora));
    }

    @Override public void cambiarEstadoObjetivo(long userId, long objetivoId, String estado, LocalDateTime ahora) {
        boolean abierto = "ACTUAL".equals(estado) || "SIGUIENTE".equals(estado);
        Timestamp t = Timestamp.valueOf(ahora);
        jdbc.update(MisionSql.CAMBIAR_ESTADO_OBJETIVO, estado, abierto ? null : t, "ACTUAL".equals(estado) ? t : null, userId, objetivoId);
    }

    @Override public void quitarAsignacion(long userId, long objetivoId) { jdbc.update(MisionSql.QUITAR_ASIGNACION, userId, objetivoId); }
}