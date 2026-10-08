package com.example.demo.application.services;

import com.example.demo.domain.cinema.Cinema;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Qué cines mirar según la zona que pide el usuario.
 *
 * El front ofrece localidades y, en Capital Federal, barrios (Palermo,
 * Belgrano, Caballito...), pero el directorio no siempre trae el barrio en la
 * ciudad del cine: muchos de Capital figuran como "Capital Federal" (ej.
 * "Cinemark Palermo", "Cinemark Abasto"). Por eso:
 * - Un cine es de una localidad si su ciudad es esa, o si su NOMBRE la
 *   contiene como palabra ("Cinemark Palermo" es de Palermo).
 * - Si en la localidad pedida no hay funciones, se amplía a toda la
 *   provincia (en CABA, a toda la ciudad) y se avisa que se amplió, para
 *   que el front pueda decírselo al usuario.
 */
final class ZonaGeografica {

    private ZonaGeografica() {}

    record Resultado<T>(List<T> items, boolean ampliada) {}

    static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    // Formas en que se escribe Capital Federal: el front manda "caba" y el
    // directorio guarda lo que informa agendadecine, que puede ser otra.
    private static final Set<String> ALIAS_CABA = Set.of(
            "caba", "capital-federal", "ciudad-autonoma-de-buenos-aires",
            "ciudad-autonoma-buenos-aires", "ciudad-de-buenos-aires", "c-a-b-a");

    // Provincia en forma comparable: sin tildes, en minúsculas y con guiones
    // ("Córdoba" y "cordoba" → "cordoba"; "Entre Ríos" → "entre-rios"), y todas
    // las formas de Capital Federal como "caba". La provincia de Buenos Aires
    // ("buenos-aires") NO es Capital.
    static String slugProvincia(String provincia) {
        String slug = normalizar(provincia).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return ALIAS_CABA.contains(slug) ? "caba" : slug;
    }

    // De todos los cines, los de la provincia pedida (comparación tolerante).
    static List<Cinema> cinesDeLaProvincia(List<Cinema> todos, String provincia) {
        String buscada = slugProvincia(provincia);
        return todos.stream()
                .filter(c -> slugProvincia(c.getProvince()).equals(buscada))
                .toList();
    }

    // De los cines de una provincia, los de la localidad pedida. Sin localidad,
    // todos.
    static List<Cinema> cinesDeLaLocalidad(List<Cinema> cinesDeLaProvincia, String localidad) {
        String buscada = normalizar(localidad);
        if (buscada.isEmpty()) return cinesDeLaProvincia;

        Pattern comoPalabra = Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(buscada) + "($|[^a-z0-9])");
        return cinesDeLaProvincia.stream()
                .filter(c -> normalizar(c.getCity()).equals(buscada)
                        || comoPalabra.matcher(normalizar(c.getName())).find())
                .toList();
    }

    // Busca primero en la localidad; si no hay nada (y se había pedido una
    // localidad), busca en toda la provincia. "ampliada" es true solo si esa
    // segunda búsqueda trajo algo.
    static <T> Resultado<T> conAmpliacion(boolean hayLocalidad,
                                          Supplier<List<T>> enLaLocalidad,
                                          Supplier<List<T>> enLaProvincia) {
        List<T> propios = enLaLocalidad.get();
        if (!propios.isEmpty() || !hayLocalidad) return new Resultado<>(propios, false);
        List<T> ampliados = enLaProvincia.get();
        return new Resultado<>(ampliados, !ampliados.isEmpty());
    }
}