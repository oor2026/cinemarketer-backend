package com.example.demo.infrastructure.external.tmdb;

import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.domain.mision.CatalogoPeliculas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Género y director de una película, desde TMDB. Los dos pedidos (getMovieDetails y getMovieCredits) ya tienen @Cacheable en
 * TmdbService, así que después de la primera vez no salen a la red. Cualquier falla devuelve vacío: la misión simplemente no cuenta.
 */
@Component
public class TmdbCatalogo implements CatalogoPeliculas {

    private static final Logger log = LoggerFactory.getLogger(TmdbCatalogo.class);
    private final TmdbService tmdbService;

    public TmdbCatalogo(TmdbService tmdbService) { this.tmdbService = tmdbService; }

    @Override public Set<Long> generos(long movieId) {
        Set<Long> r = new HashSet<>();
        try {
            TmdbMovieDto d = tmdbService.getMovieDetails(movieId);
            if (d != null && d.getGenres() != null) d.getGenres().forEach(g -> r.add(Long.valueOf(String.valueOf(g.getId()))));
        } catch (Exception e) { log.warn("[misiones] no se pudieron obtener los géneros de la película {}: {}", movieId, e.getMessage()); }
        return r;
    }

    @Override public Set<Long> directores(long movieId) {
        Set<Long> r = new HashSet<>();
        try {
            Object c = tmdbService.getMovieCredits(movieId);
            if (c instanceof Map<?, ?> m && m.get("crew") instanceof Iterable<?> crew) {
                for (Object p : crew) {
                    if (p instanceof Map<?, ?> persona && "Director".equals(persona.get("job")) && persona.get("id") instanceof Number id) r.add(id.longValue());
                }
            }
        } catch (Exception e) { log.warn("[misiones] no se pudo obtener el director de la película {}: {}", movieId, e.getMessage()); }
        return r;
    }

    /** Cuántos del reparto cuentan como «elenco principal»: así un cameo no suma para una misión de actor o actriz. */
    private static final int ELENCO_PRINCIPAL = 10;

    /** Una saga es una colección de TMDB. Se mira su lista de películas (getCollection tiene caché) y se ve si esta película está. */
    @Override public Set<Long> sagas(long movieId, Collection<Long> candidatas) {
        Set<Long> r = new HashSet<>();
        for (Long saga : candidatas) {
            try {
                Object c = tmdbService.getCollection(saga);
                if (c instanceof Map<?, ?> m && m.get("parts") instanceof Iterable<?> partes) {
                    for (Object p : partes) {
                        if (p instanceof Map<?, ?> pelicula && pelicula.get("id") instanceof Number id && id.longValue() == movieId) { r.add(saga); break; }
                    }
                }
            } catch (Exception e) { log.warn("[misiones] no se pudo obtener la saga {}: {}", saga, e.getMessage()); }
        }
        return r;
    }

    @Override public Set<Long> elenco(long movieId) {
        Set<Long> r = new HashSet<>();
        try {
            Object c = tmdbService.getMovieCredits(movieId);
            if (c instanceof Map<?, ?> m && m.get("cast") instanceof Iterable<?> reparto) {
                int puesto = 0;
                for (Object p : reparto) {
                    if (puesto++ >= ELENCO_PRINCIPAL) break;                       // el reparto viene ordenado por importancia
                    if (p instanceof Map<?, ?> persona && persona.get("id") instanceof Number id) r.add(id.longValue());
                }
            }
        } catch (Exception e) { log.warn("[misiones] no se pudo obtener el elenco de la película {}: {}", movieId, e.getMessage()); }
        return r;
    }
}
