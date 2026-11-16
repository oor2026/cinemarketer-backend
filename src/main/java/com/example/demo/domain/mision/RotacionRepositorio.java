package com.example.demo.domain.mision;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** El catálogo automático contra PostgreSQL. */
@Repository
public class RotacionRepositorio implements RotacionDatos {

    /** La clave del candado (cualquier número fijo que no use otra cosa de la aplicación). */
    private static final long CLAVE_BLOQUEO = 7_300_301L;

    private record Existente(long id, String nombre, int meta, long misionId, long selloId, boolean activo, boolean excluido) {}

    private static final RowMapper<Existente> EXISTENTE = (rs, n) -> new Existente(rs.getLong("id"), rs.getString("nombre"), rs.getInt("meta"),
            rs.getLong("mision_id"), rs.getLong("sello_id"), rs.getBoolean("activo"), rs.getBoolean("excluido"));

    private final JdbcTemplate jdbc;

    public RotacionRepositorio(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public Optional<Resultado> aplicar(List<Objetivo> objetivos) {
        Boolean libre = jdbc.queryForObject(RotacionSql.BLOQUEO, Boolean.class, CLAVE_BLOQUEO);
        if (!Boolean.TRUE.equals(libre)) return Optional.empty();           // otro proceso lo está corriendo

        int creados = 0, actualizados = 0, sinCambios = 0, omitidos = 0;
        for (Objetivo o : objetivos) {
            List<Existente> ya = jdbc.query(RotacionSql.BUSCAR_OBJETIVO, EXISTENTE, o.familia(), o.tmdbId());
            if (ya.isEmpty()) { crear(o); creados++; continue; }
            Existente e = ya.get(0);
            if (e.excluido() || !e.activo()) { omitidos++; continue; }       // lo que el admin sacó, no se toca
            completarImagenes(e.selloId(), o);                                // las sagas que ya estaban reciben su imagen sin pisar la que haya subido el admin
            if (e.meta() == o.meta() && e.nombre().equals(o.nombre())) {
                jdbc.update(RotacionSql.TOCAR_OBJETIVO, o.popularidad(), e.id());
                sinCambios++;
            } else {                                                          // p. ej. la saga estrenó otra película: sube la meta
                jdbc.update(RotacionSql.ACTUALIZAR_OBJETIVO, o.nombre(), o.meta(), o.popularidad(), e.id());
                jdbc.update(RotacionSql.ACTUALIZAR_MISION, o.meta(), RotacionTextos.descripcion(o.familia(), o.meta(), o.nombre()),
                        RotacionTextos.cumplida(o.familia(), o.meta(), o.nombre()), e.misionId());
                jdbc.update(RotacionSql.ACTUALIZAR_SELLO, RotacionTextos.cumplida(o.familia(), o.meta(), o.nombre()), e.selloId());
                actualizados++;
            }
        }
        return Optional.of(new Resultado(creados, actualizados, sinCambios, omitidos));
    }

    /** Crea el sello, la misión y el objetivo. Si el nombre ya lo usa otra misión o sello, le agrega el id de TMDB para distinguirlo. */
    private void crear(Objetivo o) {
        String titulo = RotacionTextos.recortar(RotacionTextos.titulo(o.familia(), o.nombre()), RotacionTextos.MAX_TITULO);     // el sello admite 80 caracteres
        if (contar(RotacionSql.MISION_CON_TITULO, titulo) > 0 || contar(RotacionSql.SELLO_USADO, titulo) > 0) {
            String sufijo = " (" + o.tmdbId() + ")";
            titulo = RotacionTextos.recortar(titulo, RotacionTextos.MAX_TITULO - sufijo.length()) + sufijo;
        }
        String descripcion = RotacionTextos.descripcion(o.familia(), o.meta(), o.nombre());
        String cumplida = RotacionTextos.cumplida(o.familia(), o.meta(), o.nombre());
        String categoria = RotacionTextos.categoria(o.familia());

        List<Long> libre = jdbc.query(RotacionSql.SELLO_LIBRE, (rs, n) -> rs.getLong(1), titulo);      // un sello suelto con ese nombre se reutiliza
        long selloId;
        if (libre.isEmpty()) {
            selloId = jdbc.queryForObject(RotacionSql.INSERTAR_SELLO, Long.class, titulo, cumplida, categoria, o.imagenUrl(), o.fondoUrl());
        } else {
            selloId = libre.get(0);
            jdbc.update(RotacionSql.ACTUALIZAR_SELLO, cumplida, selloId);
            completarImagenes(selloId, o);
        }
        Long genero = "GENERO".equals(o.familia()) ? o.tmdbId() : null, saga = "SAGA".equals(o.familia()) ? o.tmdbId() : null;
        Long actor = "ACTOR".equals(o.familia()) ? o.tmdbId() : null, director = "DIRECTOR".equals(o.familia()) ? o.tmdbId() : null;
        boolean serie = "SERIE".equals(o.familia());
        long misionId = jdbc.queryForObject(RotacionSql.INSERTAR_MISION, Long.class, titulo, descripcion, cumplida, categoria, o.meta(),
                serie ? "pasos" : "votos", serie ? "SERIE" : "PELICULA",
                genero, saga, actor, director, selloId, o.familia(), serie ? o.tmdbId() : null);
        jdbc.update(RotacionSql.INSERTAR_OBJETIVO, o.familia(), o.tmdbId(), o.nombre(), o.meta(), misionId, o.popularidad());
    }

    private void completarImagenes(long selloId, Objetivo o) {
        if (o.imagenUrl() == null && o.fondoUrl() == null) return;
        jdbc.update(RotacionSql.COMPLETAR_IMAGENES, o.imagenUrl(), o.fondoUrl(), selloId, o.imagenUrl(), o.fondoUrl());
    }

    private int contar(String sql, String nombre) {
        Integer n = jdbc.queryForObject(sql, Integer.class, nombre);
        return n == null ? 0 : n;
    }

    @Override
    public List<Long> personasSinFoto(String familia) { return jdbc.query(RotacionSql.PERSONAS_SIN_FOTO, (rs, n) -> rs.getLong(1), familia); }

    @Override
    public void ponerFoto(String familia, long tmdbId, String url) { jdbc.update(RotacionSql.PONER_FOTO, url, familia, tmdbId); }

    @Override
    public int cantidadObjetivos() {
        Integer n = jdbc.queryForObject(RotacionSql.CONTAR_OBJETIVOS, Integer.class);
        return n == null ? 0 : n;
    }
}