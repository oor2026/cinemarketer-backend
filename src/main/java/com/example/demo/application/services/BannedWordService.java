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
     * Normaliza el texto: minúsculas, sin tildes, sin caracteres especiales.
     * Permite detectar variantes como "estúpido", "e s t u p i d o", "estup1do".
     */
    private String normalizar(String texto) {
        if (texto == null) return "";
        // Quitar tildes y diacríticos
        String sinTildes = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Quitar caracteres no alfanuméricos excepto espacios
        return sinTildes.replaceAll("[^a-z0-9 ]", "");
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
        return texto.contains(palabra);
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
