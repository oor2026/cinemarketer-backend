package com.example.demo.web.controllers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recorta lo que se le entrega a un visitante SIN sesión.
 *
 * La entidad Publication trae datos internos (denuncias, estado de moderación, motivos de revisión,
 * puntos otorgados, fechas de ocultamiento...) que un visitante no debe ver. Acá se trabaja con una
 * lista de campos PERMITIDOS, no de prohibidos: si mañana se agrega un campo interno nuevo a la
 * entidad, queda afuera por defecto en lugar de filtrarse sin que nadie lo note.
 *
 * Los campos permitidos son exactamente los que lee la página pública (publicacion.html) y las
 * herramientas de Creator Tools que se dibujan ahí.
 */
public final class PublicPublicationSanitizer {

    private PublicPublicationSanitizer() {}

    private static final Set<String> CAMPOS_PUBLICACION = Set.of(
            "id", "user", "movieId", "title", "territoryGroup", "tone", "content", "spoiler", "createdAt",
            "imageUrls", "videoUrl", "videoUid",
            "movieFichaEnabled", "countdownEnabled", "countdownCountryCode",
            "votacionEnabled", "rankingEnabled", "rankingFormato", "rankingModoTexto",
            "triviaEnabled", "triviaTipo", "triviaReferenciaTipo", "triviaReferenciaId",
            "trailerEnabled", "trailerYoutubeKey");

    private static final Set<String> CAMPOS_AUTOR = Set.of("id", "name", "avatarUrl");
    private static final Set<String> CAMPOS_ITEM_RANKING = Set.of("orden", "movieId", "texto");

    /** La publicación en sí (ya convertida a Map): solo los campos permitidos y del autor solo id, nombre y avatar. */
    public static Map<String, Object> publicacion(Map<String, Object> completa) {
        Map<String, Object> salida = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : completa.entrySet()) {
            if (!CAMPOS_PUBLICACION.contains(e.getKey())) continue;
            salida.put(e.getKey(), "user".equals(e.getKey()) ? autor(e.getValue()) : e.getValue());
        }
        return salida;
    }

    private static Object autor(Object valor) {
        if (!(valor instanceof Map<?, ?> m)) return null;
        Map<String, Object> salida = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String clave = String.valueOf(e.getKey());
            if (CAMPOS_AUTOR.contains(clave)) salida.put(clave, e.getValue());
        }
        return salida;
    }

    /**
     * Votación vista por un visitante: nunca "ya votó" ni opción elegida. Mientras está abierta no se
     * entregan los porcentajes (los resultados parciales se ven recién al votar o al cerrar).
     * Si no se sabe si está cerrada, se la trata como abierta.
     */
    public static Map<String, Object> votacion(Map<String, Object> original) {
        Map<String, Object> salida = new LinkedHashMap<>(original);
        boolean cerrada = Boolean.TRUE.equals(original.get("cerrada"));
        salida.put("yaVoto", false);
        salida.put("opcionElegidaId", null);
        salida.put("opciones", opciones(original.get("opciones"), cerrada ? Set.of() : Set.of("porcentaje")));
        return salida;
    }

    /**
     * Trivia vista por un visitante: nunca "ya respondió" ni acierto propio. Mientras está abierta NO se
     * entrega cuál es la opción correcta ni el porcentaje de acierto: recién al cerrar se revelan.
     * Si no se sabe si está cerrada, se la trata como abierta.
     */
    public static Map<String, Object> trivia(Map<String, Object> original) {
        Map<String, Object> salida = new LinkedHashMap<>(original);
        boolean cerrada = Boolean.TRUE.equals(original.get("cerrada"));
        salida.put("yaRespondio", false);
        salida.put("acerto", null);
        salida.put("opcionElegidaId", null);
        if (!cerrada) salida.remove("porcentajeAcierto");
        salida.put("opciones", opciones(original.get("opciones"), cerrada ? Set.of() : Set.of("esCorrecta")));
        return salida;
    }

    /**
     * Un comentario (ya convertido a Map): el usuario anidado se recorta a id, nombre y avatar.
     * El resto del comentario no cambia. La entidad PublicationComment lleva el User completo adentro
     * (mail, DNI, teléfono, etc.), y esto no depende de cómo estén anotadas las entidades.
     */
    public static Map<String, Object> comentario(Map<String, Object> original) {
        Map<String, Object> salida = new LinkedHashMap<>(original);
        if (salida.containsKey("user")) salida.put("user", autor(salida.get("user")));
        // La entidad también lleva la Publication entera adentro (con denuncias, estado de moderación,
        // motivo de revisión...). El front no la usa: se deja solo el id.
        if (salida.containsKey("publication")) salida.put("publication", soloId(salida.get("publication")));
        return salida;
    }

    private static Object soloId(Object valor) {
        if (!(valor instanceof Map<?, ?> m)) return null;
        Map<String, Object> salida = new LinkedHashMap<>();
        if (m.containsKey("id")) salida.put("id", m.get("id"));
        return salida;
    }

    /** Lista de comentarios (cada elemento ya convertido a Map). Lo que no sea un Map pasa tal cual. */
    public static List<Object> comentarios(Object lista) {
        List<Object> salida = new ArrayList<>();
        if (!(lista instanceof List<?> l)) return salida;
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> copia = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) copia.put(String.valueOf(e.getKey()), e.getValue());
                salida.add(comentario(copia));
            } else {
                salida.add(o);
            }
        }
        return salida;
    }

    /** Página de comentarios (Page ya convertida a Map): se recorta el usuario de cada comentario de "content"; la paginación no cambia. */
    public static Map<String, Object> paginaDeComentarios(Map<String, Object> pagina) {
        Map<String, Object> salida = new LinkedHashMap<>(pagina);
        if (salida.containsKey("content")) salida.put("content", comentarios(salida.get("content")));
        return salida;
    }

    /**
     * Una publicación del feed, del perfil o del detalle (ya convertida a Map): solo se recorta el
     * usuario anidado a id, nombre y avatar. El resto de la publicación no cambia.
     */
    public static Map<String, Object> publicacionConAutorRecortado(Map<String, Object> original) {
        Map<String, Object> salida = new LinkedHashMap<>(original);
        if (salida.containsKey("user")) salida.put("user", autor(salida.get("user")));
        return salida;
    }

    /** Página de publicaciones (Page ya convertida a Map): se recorta el usuario de cada una; la paginación no cambia. */
    public static Map<String, Object> paginaDePublicaciones(Map<String, Object> pagina) {
        Map<String, Object> salida = new LinkedHashMap<>(pagina);
        if (salida.get("content") instanceof List<?> lista) {
            List<Object> limpia = new ArrayList<>();
            for (Object o : lista) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> copia = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) copia.put(String.valueOf(e.getKey()), e.getValue());
                    limpia.add(publicacionConAutorRecortado(copia));
                } else {
                    limpia.add(o);
                }
            }
            salida.put("content", limpia);
        }
        return salida;
    }

    /** Ítems del ranking: solo orden, película y texto del autor. */
    public static List<Map<String, Object>> ranking(List<Map<String, Object>> items) {
        List<Map<String, Object>> salida = new ArrayList<>();
        if (items == null) return salida;
        for (Map<String, Object> item : items) {
            Map<String, Object> limpio = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : item.entrySet()) {
                if (CAMPOS_ITEM_RANKING.contains(e.getKey())) limpio.put(e.getKey(), e.getValue());
            }
            salida.add(limpio);
        }
        return salida;
    }

    private static Object opciones(Object valor, Set<String> ocultar) {
        if (!(valor instanceof List<?> lista)) return valor;
        List<Object> salida = new ArrayList<>();
        for (Object o : lista) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> copia = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    String clave = String.valueOf(e.getKey());
                    if (!ocultar.contains(clave)) copia.put(clave, e.getValue());
                }
                salida.add(copia);
            } else {
                salida.add(o);
            }
        }
        return salida;
    }
}