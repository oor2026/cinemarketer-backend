package com.example.demo.application.services;

import com.example.demo.domain.mision.CatalogoTmdb;
import com.example.demo.domain.mision.SagasCatalogo;
import com.example.demo.domain.mision.SagasPeliculasDatos;
import com.example.demo.domain.mision.SagasPeliculasDatos.Cambio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * El universo de películas de cada saga, armado SOLO desde sagas.json (la misma lista que usa el asistente del feed):
 *   · sagas de colección: las películas de la colección de TMDB + las «extras» que sagas.json agrega;
 *   · sagas armadas a mano: las películas que sagas.json lista.
 * Cuentan las ya ESTRENADAS y con póster (como el asistente, que no muestra las que no tienen póster). Una película que todavía no se estrenó
 * entra sola el día que se estrena.
 *
 * Se guarda en saga_pelicula: de ahí lo leen los votos en vivo, el historial y la suspensión de sellos. Si TMDB falla al consultar la saga
 * o una película, no se toca lo que ya estaba guardado (una caída de red nunca achica una saga).
 */
@Service
public class SagasUniverso {

    private static final Logger log = LoggerFactory.getLogger(SagasUniverso.class);

    /** peliculas = ids de TMDB de las películas que cuentan. posterUrl / fondoUrl = la imagen de la colección (null en las sagas armadas a mano). */
    public record Universo(Set<Long> peliculas, String posterUrl, String fondoUrl) {}

    private final SagasCatalogo catalogo;
    private final CatalogoTmdb tmdb;
    private final SagasPeliculasDatos datos;

    @Autowired
    public SagasUniverso(SagasCatalogo catalogo, CatalogoTmdb tmdb, SagasPeliculasDatos datos) {
        this.catalogo = catalogo;
        this.tmdb = tmdb;
        this.datos = datos;
    }

    public List<SagasCatalogo.Saga> sagas() { return catalogo.todas(); }

    /**
     * Arma el universo de una saga y lo guarda. null si no se pudo consultar TMDB (en ese caso no se cambia nada).
     * Lanza excepción solo ante un error inesperado: quien llama saltea esa saga.
     */
    public Universo actualizar(SagasCatalogo.Saga saga) {
        Set<Long> guardadas = datos.peliculas(saga.id());
        Universo u = resolver(saga, guardadas);
        if (u == null) return null;
        Cambio c = datos.reemplazar(saga.id(), u.peliculas());
        if (c.hubo()) log.info("[sagas] {} ({}): +{} / -{} películas, ahora {}", saga.nombre(), saga.id(), c.sumadas(), c.sacadas(), u.peliculas().size());
        return u;
    }

    /** Sin guardar nada. {@code guardadas} = lo que ya estaba: una película que no se pudo consultar se conserva si ya estaba. */
    Universo resolver(SagasCatalogo.Saga saga, Set<Long> guardadas) {
        Set<Long> ids = new LinkedHashSet<>();
        String poster = null, fondo = null;
        if (saga.coleccion() != null) {
            Set<Long> partes = tmdb.peliculasEstrenadasDeColeccion(saga.coleccion());
            if (partes == null) {
                log.warn("[sagas] no se pudo consultar la colección {} de «{}»: se deja como estaba", saga.coleccion(), saga.nombre());
                return null;
            }
            ids.addAll(partes);
            CatalogoTmdb.Saga info = tmdb.saga(saga.coleccion());
            if (info != null) { poster = info.posterUrl(); fondo = info.fondoUrl(); }
        }
        for (Long id : saga.peliculas()) {
            if (ids.contains(id)) continue;
            Boolean estrenada;
            try { estrenada = tmdb.peliculaEstrenada(id); } catch (Exception e) { estrenada = null; }
            if (estrenada == null) {                         // no se pudo saber: si ya estaba, se queda
                if (guardadas.contains(id)) ids.add(id);
                else log.warn("[sagas] no se pudo consultar la película {} de «{}»: no se cuenta por ahora", id, saga.nombre());
            } else if (estrenada) {
                ids.add(id);
            }
        }
        return new Universo(ids, poster, fondo);
    }

    /** Para el admin: cuántas películas tiene guardadas cada saga de sagas.json. */
    public List<Map<String, Object>> resumen() {
        List<Map<String, Object>> r = new ArrayList<>();
        for (SagasCatalogo.Saga s : catalogo.todas()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("nombre", s.nombre());
            m.put("peliculasGuardadas", datos.peliculas(s.id()).size());
            r.add(m);
        }
        return r;
    }
}
