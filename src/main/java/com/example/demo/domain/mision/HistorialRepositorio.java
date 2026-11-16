package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionLogica.Mision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** HistorialDatos contra PostgreSQL (JdbcTemplate). La tabla usuario_historial se crea con 14_historial.sql. */
@Repository
public class HistorialRepositorio implements HistorialDatos {

    private final JdbcTemplate jdbc;

    public HistorialRepositorio(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static LocalDateTime fecha(Timestamp t) { return t == null ? null : t.toLocalDateTime(); }

    @Override public List<Evento> eventos(long userId) {
        return jdbc.query(HistorialSql.EVENTOS,
                (rs, n) -> new Evento(rs.getString("tipo"), rs.getString("contenido"), rs.getLong("id"), fecha(rs.getTimestamp("cuando"))),
                userId, userId, userId, userId, userId, userId, userId, userId, userId, userId);
    }

    @Override public Map<Long, Registro> registros(long userId) {
        Map<Long, Registro> r = new HashMap<>();
        jdbc.query(HistorialSql.REGISTROS, (rs, n) -> r.put(rs.getLong("mision_id"),
                new Registro(rs.getLong("mision_id"), rs.getString("estado"), rs.getInt("base"), fecha(rs.getTimestamp("calculado_at")))), userId);
        return r;
    }

    @Override public Map<Long, LocalDateTime> primerAvanceEnVivo(long userId) {
        Map<Long, LocalDateTime> r = new HashMap<>();
        jdbc.query(HistorialSql.PRIMER_AVANCE, (rs, n) -> r.put(rs.getLong("mision_id"), fecha(rs.getTimestamp("created_at"))), userId);
        return r;
    }

    @Override public void guardar(long userId, long misionId, String estado, int base, LocalDateTime ahora) {
        jdbc.update(HistorialSql.GUARDAR, userId, misionId, estado, base, Timestamp.valueOf(ahora));
    }

    @Override public boolean marcarReclamado(long userId, long misionId, LocalDateTime ahora) {
        return jdbc.update(HistorialSql.MARCAR_RECLAMADO, Timestamp.valueOf(ahora), userId, misionId) > 0;
    }

    @Override public int contarPendientes(long userId) {
        Integer n = jdbc.queryForObject(HistorialSql.CONTAR_PENDIENTES, Integer.class, userId);
        return n == null ? 0 : n;
    }

    @Override public List<Mision> rotativasParaEvaluar(long userId, LocalDateTime repasoDesde) {
        return jdbc.query(HistorialSql.ROTATIVAS_PARA_EVALUAR, MisionRepositorio.MISION, userId, userId, userId, Timestamp.valueOf(repasoDesde));
    }

    @Override public List<Mision> misionesPorIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query(HistorialSql.misionesPorIds(ids.size()), MisionRepositorio.MISION, ids.toArray());
    }

    @Override public Long objetivoDeMision(long misionId) {
        List<Long> r = jdbc.query(HistorialSql.OBJETIVO_DE_MISION, (rs, n) -> rs.getLong("id"), misionId);
        return r.isEmpty() ? null : r.get(0);
    }
}