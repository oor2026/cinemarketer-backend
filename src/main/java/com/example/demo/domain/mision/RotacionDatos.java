package com.example.demo.domain.mision;

import java.util.List;
import java.util.Optional;

/** Lo que el catálogo automático necesita de la base. La implementación real es RotacionRepositorio (SQL). */
public interface RotacionDatos {

    /**
     * Un objetivo que el catálogo quiere tener: la saga, el actor/actriz, el director o el género, con su meta.
     * imagenUrl / fondoUrl: la imagen del sello (p. ej. el póster de la saga) y la de fondo del detalle. null = ícono de su categoría.
     */
    record Objetivo(String familia, long tmdbId, String nombre, int meta, double popularidad, String imagenUrl, String fondoUrl) {
        public Objetivo(String familia, long tmdbId, String nombre, int meta, double popularidad) { this(familia, tmdbId, nombre, meta, popularidad, null, null); }
    }

    /** creados = objetivos nuevos · actualizados = cambió su meta o nombre · sinCambios · omitidos = el admin los excluyó o desactivó. */
    record Resultado(int creados, int actualizados, int sinCambios, int omitidos) {}

    /**
     * Crea los objetivos que faltan (con su misión y su sello) y actualiza los que cambiaron, todo en una transacción. Nunca borra ni
     * desactiva nada: el catálogo solo crece. Si otro proceso ya lo está corriendo devuelve vacío y no hace nada.
     */
    Optional<Resultado> aplicar(List<Objetivo> objetivos);

    int cantidadObjetivos();

    /** Los ids de TMDB de los actores/directores activos del catálogo cuyo sello todavía no tiene imagen. */
    List<Long> personasSinFoto(String familia);

    /** Le pone la foto al sello de ese actor/director, solo si todavía no tiene imagen (no pisa la que haya subido el admin). */
    void ponerFoto(String familia, long tmdbId, String url);

    /**
     * Los ids de TMDB de las series que los usuarios de Cinemarketer votaron, comentaron o recomendaron y que todavía no están en el catálogo
     * (ni siquiera excluidas por el admin), las más movidas primero. Como mucho {@code max}.
     */
    default List<Long> seriesDeLaComunidad(int max) { return List.of(); }
}