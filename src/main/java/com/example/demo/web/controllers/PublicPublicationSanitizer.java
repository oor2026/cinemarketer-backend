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
