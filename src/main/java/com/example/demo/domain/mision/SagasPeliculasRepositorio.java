package com.example.demo.domain.mision;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SagasPeliculasDatos contra PostgreSQL (JdbcTemplate). La tabla saga_pelicula se crea con 01_migracion_sagas.sql. */
@Repository
public class SagasPeliculasRepositorio implements SagasPeliculasDatos {

    static final String PELICULAS = "SELECT movie_id FROM saga_pelicula WHERE saga_id = ?";
    static final String SAGAS_DE = "SELECT saga_id FROM saga_pelicula WHERE movie_id = ?";
    static final String SUMAR = "INSERT INTO saga_pelicula (saga_id, movie_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
    static final String SACAR = "DELETE FROM saga_pelicula WHERE saga_id = ? AND movie_id = ?";

    private final JdbcTemplate jdbc;

    public SagasPeliculasRepositorio(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Set<Long> peliculas(long sagaId) {
        return new HashSet<>(jdbc.query(PELICULAS, (rs, n) -> rs.getLong(1), sagaId));
    }

    @Override public Set<Long> sagasDe(long movieId, Collection<Long> candidatas) {
        Set<Long> r = new HashSet<>(jdbc.query(SAGAS_DE, (rs, n) -> rs.getLong(1), movieId));
        r.retainAll(candidatas);
        return r;
    }

    @Override @Transactional
    public Cambio reemplazar(long sagaId, Set<Long> nuevas) {
        Set<Long> actuales = peliculas(sagaId);
        List<Long> sumar = nuevas.stream().filter(id -> !actuales.contains(id)).toList();
        List<Long> sacar = actuales.stream().filter(id -> !nuevas.contains(id)).toList();
        for (Long id : sumar) jdbc.update(SUMAR, sagaId, id);
        for (Long id : sacar) jdbc.update(SACAR, sagaId, id);
        return new Cambio(sumar.size(), sacar.size());
    }
}
