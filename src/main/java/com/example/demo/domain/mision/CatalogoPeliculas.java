package com.example.demo.domain.mision;

import java.util.Collection;
import java.util.Set;

/** De qué géneros es una película y quién la dirigió (para las misiones cualitativas). Si no se sabe, devuelve vacío. */
public interface CatalogoPeliculas {
    Set<Long> generos(long movieId);
    Set<Long> directores(long movieId);

    /** De las sagas (colecciones de TMDB) candidatas, a cuáles pertenece la película. */
    Set<Long> sagas(long movieId, Collection<Long> candidatas);

    /** Elenco principal de la película: los ids de TMDB de los primeros actores y actrices del reparto. */
    Set<Long> elenco(long movieId);
}
