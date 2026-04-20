package com.example.demo.web.controllers;

import com.example.demo.domain.moderation.BannedWord;
import com.example.demo.domain.moderation.BannedWordRepository;
import com.example.demo.domain.moderation.BannedWordSeverity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/banned-words")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:63342")
public class AdminBannedWordsController {

    private final BannedWordRepository bannedWordRepository;

    /**
     * GET /api/admin/banned-words
     * Lista todas las palabras prohibidas ordenadas alfabéticamente
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<BannedWord>> listar() {
        return ResponseEntity.ok(bannedWordRepository.findAllByOrderByWordAsc());
    }

    /**
     * POST /api/admin/banned-words
     * Agrega una nueva palabra prohibida
     * Body: { "word": "insulto", "severity": "BLOCK" }
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> agregar(@RequestBody Map<String, String> body) {
        String word = body.get("word");
        String severityStr = body.get("severity");

        if (word == null || word.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La palabra es obligatoria"));
        }

        String wordNormalizada = word.trim().toLowerCase();

        if (bannedWordRepository.existsByWordIgnoreCase(wordNormalizada)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La palabra ya existe en la lista"));
        }

        BannedWordSeverity severity;
        try {
            severity = severityStr != null
                    ? BannedWordSeverity.valueOf(severityStr.toUpperCase())
                    : BannedWordSeverity.BLOCK;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Severidad inválida. Usar BLOCK o REVIEW"));
        }

        BannedWord nueva = new BannedWord();
        nueva.setWord(wordNormalizada);
        nueva.setSeverity(severity);
        bannedWordRepository.save(nueva);

        return ResponseEntity.ok(Map.of(
                "message", "Palabra agregada correctamente",
                "word", wordNormalizada,
                "severity", severity.name()
        ));
    }

    /**
     * DELETE /api/admin/banned-words/{id}
     * Elimina una palabra prohibida por ID
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        if (!bannedWordRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bannedWordRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Palabra eliminada correctamente"));
    }
}
