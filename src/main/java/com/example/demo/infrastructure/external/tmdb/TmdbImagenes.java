package com.example.demo.infrastructure.external.tmdb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reduce la respuesta de /images de TMDB a lo que usan el modal de película y el de serie: los logos del título y, de
 * los pósters, solo el archivo y el idioma de su texto impreso (un póster con idioma trae el título impreso; sin idioma,
 * no). Se descartan los fondos (backdrops) y el resto de los campos: la respuesta completa pesa cientos de KB y el
 * celular no los usa.
 */
final class TmdbImagenes {

    /** Idiomas que se piden a TMDB: español, inglés y sin idioma (logos internacionales). Fijo en el servidor. */
    static final String IDIOMAS = "es,en,null";

    private static final String[] CAMPOS_LOGO = {"file_path", "iso_639_1", "vote_average", "width", "height"};
    private static final String[] CAMPOS_POSTER = {"file_path", "iso_639_1"};

    private TmdbImagenes() {
    }

    /** Siempre devuelve las dos listas, vacías si TMDB no mandó nada o mandó algo con otra forma. */
    static Map<String, Object> reducir(Object cuerpo) {
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("logos", copiarLista(cuerpo, "logos", CAMPOS_LOGO));
        salida.put("posters", copiarLista(cuerpo, "posters", CAMPOS_POSTER));
        return salida;
    }

    private static List<Map<String, Object>> copiarLista(Object cuerpo, String clave, String[] campos) {
        List<Map<String, Object>> lista = new ArrayList<>();
        if (!(cuerpo instanceof Map<?, ?> mapa)) return lista;
        if (!(mapa.get(clave) instanceof List<?> items)) return lista;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> origen) || origen.get("file_path") == null) continue;
            Map<String, Object> copia = new LinkedHashMap<>();
            for (String campo : campos) copia.put(campo, origen.get(campo));
            lista.add(copia);
        }
        return lista;
    }
}
