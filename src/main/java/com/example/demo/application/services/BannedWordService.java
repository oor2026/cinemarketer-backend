package com.example.demo.application.services;

import com.example.demo.domain.moderation.BannedWordRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;

@Service
public class BannedWordService {

    private final BannedWordRepository bannedWordRepository;

    public BannedWordService(BannedWordRepository bannedWordRepository) {
        this.bannedWordRepository = bannedWordRepository;
    }

    /**
     * Mapa de sustitución: caracteres que se usan para evadir el filtro
     * reemplazando letras, sin que el ojo humano note la diferencia.
     * - Leetspeak: 0→o, 1→i, 3→e, 4→a, 5→s, 7→t, 8→b (ej: "put4", "pel0tudo")
     * - Homoglifos: letras cirílicas/griegas visualmente idénticas a las latinas
     *   (ej: la "е" cirílica en "pеlotudo" se ve exactamente igual a la "e" latina)
     */
    private static final java.util.Map<Character, Character> SUSTITUCIONES_EVASION = new java.util.HashMap<>();
    static {
        SUSTITUCIONES_EVASION.put('0', 'o');
        SUSTITUCIONES_EVASION.put('1', 'i');
        SUSTITUCIONES_EVASION.put('3', 'e');
        SUSTITUCIONES_EVASION.put('4', 'a');
        SUSTITUCIONES_EVASION.put('5', 's');
        SUSTITUCIONES_EVASION.put('7', 't');
        SUSTITUCIONES_EVASION.put('8', 'b');
        // Cirílico visualmente idéntico al latino
        SUSTITUCIONES_EVASION.put('а', 'a'); // U+0430
        SUSTITUCIONES_EVASION.put('е', 'e'); // U+0435
        SUSTITUCIONES_EVASION.put('о', 'o'); // U+043E
        SUSTITUCIONES_EVASION.put('р', 'p'); // U+0440
        SUSTITUCIONES_EVASION.put('с', 'c'); // U+0441
        SUSTITUCIONES_EVASION.put('у', 'y'); // U+0443
        SUSTITUCIONES_EVASION.put('х', 'x'); // U+0445
        // Griego visualmente idéntico al latino
        SUSTITUCIONES_EVASION.put('ο', 'o'); // U+03BF omicron
        SUSTITUCIONES_EVASION.put('α', 'a'); // U+03B1 alfa
    }

    /**
     * Normaliza el texto: minúsculas, sin tildes, sin caracteres especiales,
     * y sin sustituciones típicas de evasión (leetspeak, homoglifos).
     * Permite detectar variantes como "estúpido", "e s t u p i d o", "estup1do", "put4".
     */
    private String normalizar(String texto) {
        if (texto == null) return "";
        // Quitar tildes y diacríticos
        String sinTildes = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Traducir caracteres de evasión conocidos a su letra latina real
        StringBuilder sustituido = new StringBuilder(sinTildes.length());
        for (char c : sinTildes.toCharArray()) {
            sustituido.append(SUSTITUCIONES_EVASION.getOrDefault(c, c));
        }
        // Quitar caracteres no alfanuméricos excepto espacios
        return sustituido.toString().replaceAll("[^a-z0-9 ]", "");
    }

    /**
     * Analiza el texto contra la lista negra.
     * Retorna el nivel de severidad más alto encontrado, o null si no hay coincidencias.
     */
    public MatchResult analizar(String texto) {
        String textoNormalizado = normalizar(texto);

        // Verificar palabras BLOCK primero (prioridad alta)
        List<String> blockWords = bannedWordRepository.findAllBlockWords();
        for (String palabra : blockWords) {
            String palabraNormalizada = normalizar(palabra);
            if (contienepalabra(textoNormalizado, palabraNormalizada)) {
                return MatchResult.BLOCK;
            }
        }

        // Verificar palabras REVIEW
        List<String> reviewWords = bannedWordRepository.findAllReviewWords();
        for (String palabra : reviewWords) {
            String palabraNormalizada = normalizar(palabra);
            if (contienepalabra(textoNormalizado, palabraNormalizada)) {
                return MatchResult.REVIEW;
            }
        }

        return MatchResult.CLEAN;
    }

    /**
     * Verifica si el texto contiene la palabra como término completo o subcadena.
     * Detecta "pelotudo" dentro de "sos un pelotudo!!!".
     */
    private boolean contienepalabra(String texto, String palabra) {
        // 1. Buscar la palabra tal cual (con word boundary)
        String patron = "(^|[^a-z0-9])" + java.util.regex.Pattern.quote(palabra) + "([^a-z0-9]|$)";
        if (java.util.regex.Pattern.compile(patron).matcher(texto).find()) {
            return true;
        }

        // 2. Detectar variante con separadores de 1 a 5 caracteres entre letras
        // Ejemplo: "p u t a", "p  u  t  a", "p-u-t-a", "p . u . t . a"
        String[] letras = palabra.split("");
        String separador = "[^a-z0-9]{1,100}";
        String patronEspaciado = String.join(separador,
                java.util.Arrays.stream(letras)
                        .map(java.util.regex.Pattern::quote)
                        .toArray(String[]::new));
        String patronCompleto = "(^|[^a-z0-9])" + patronEspaciado + "([^a-z0-9]|$)";
        return java.util.regex.Pattern.compile(patronCompleto).matcher(texto).find();
    }

    /**
     * Determina si el comentario debe ser rechazado directamente.
     */
    public boolean shouldReject(String texto) {
        return analizar(texto) == MatchResult.BLOCK;
    }

    /**
     * Determina si el comentario debe quedar en revisión pendiente.
     */
    public boolean shouldPendingReview(String texto) {
        return analizar(texto) == MatchResult.REVIEW;
    }

    /**
     * Resultado del análisis de moderación.
     */
    public enum MatchResult {
        BLOCK,   // Rechazar comentario
        REVIEW,  // Marcar como PENDING_REVIEW
        CLEAN    // Aprobar comentario
    }
}
