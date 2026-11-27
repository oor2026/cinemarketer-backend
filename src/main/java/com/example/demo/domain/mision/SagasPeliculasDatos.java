package com.example.demo.domain.mision;

import java.util.Collection;
import java.util.Set;

/**
 * Las películas de cada saga (tabla saga_pelicula): el universo que usan las misiones. Se llena desde sagas.json + TMDB
 * (ver SagasUniverso) y se lee en cada voto, así que no sale a la red. La implementación real es SagasPeliculasRepositorio (SQL).
 */
public interface SagasPeliculasDatos {

    /** sumadas / sacadas = cuántas películas entraron y salieron de la saga en este reemplazo. */
    record Cambio(int sumadas, int sacadas) {
        public boolean hubo() { return sumadas > 0 || sacadas > 0; }
    }

    /** Las películas guardadas de la saga. Vacío si nunca se guardó. */
    Set<Long> peliculas(long sagaId);

    /** De las sagas candidatas, a cuáles pertenece la película. */
    Set<Long> sagasDe(long movieId, Collection<Long> candidatas);

    /** Deja la saga con exactamente esas películas y cuenta qué cambió. */
    Cambio reemplazar(long sagaId, Set<Long> peliculas);
}
