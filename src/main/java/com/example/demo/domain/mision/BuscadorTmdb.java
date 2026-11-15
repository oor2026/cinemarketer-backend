package com.example.demo.domain.mision;

import java.util.List;

/**
 * Lo que el panel de admin le pregunta a TMDB para elegir el género, el actor o actriz, el director o la saga de una misión VIENDO su nombre
 * (y su foto), en lugar de pegar un número. La implementación real es TmdbBuscador.
 */
public interface BuscadorTmdb {

    /** imagen = la foto o el póster (URL completa, chica) o null; detalle = una línea para distinguir homónimos («Actuación · Oppenheimer, Barbie»). */
    record Opcion(long id, String nombre, String imagen, String detalle) {}

    /** Todos los géneros de películas. */
    List<Opcion> generos();

    /** Personas (actores, directores...) cuyo nombre contiene el texto. Vacío si el texto tiene menos de 2 letras. */
    List<Opcion> personas(String texto);

    /** Sagas (colecciones) cuyo nombre contiene el texto. Vacío si el texto tiene menos de 2 letras. */
    List<Opcion> sagas(String texto);

    /** Para mostrar el nombre de lo que ya está elegido al editar una misión. null si no existe o TMDB no responde. */
    Opcion genero(long id);
    Opcion persona(long id);
    Opcion saga(long id);
}
