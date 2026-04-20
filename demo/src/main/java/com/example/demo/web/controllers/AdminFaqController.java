package com.example.demo.web.controllers;

import com.example.demo.application.dtos.FaqItemDto;
import com.example.demo.application.dtos.FaqItemRequest;
import com.example.demo.domain.faq.FaqItem;
import com.example.demo.domain.faq.FaqRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/faq")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:63342")
public class AdminFaqController {

    private final FaqRepository faqRepository;

    // ── Listar todas (incluyendo inactivas) ───────────────────────────────────
    @GetMapping
    public ResponseEntity<List<FaqItemDto>> getAll() {
        List<FaqItemDto> result = faqRepository
                .findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(f -> new FaqItemDto(f.getId(), f.getQuestion(), f.getAnswer(), f.getDisplayOrder()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── Crear nueva FAQ ───────────────────────────────────────────────────────
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody FaqItemRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body("La pregunta es obligatoria.");
        }
        if (request.getAnswer() == null || request.getAnswer().isBlank()) {
            return ResponseEntity.badRequest().body("La respuesta es obligatoria.");
        }

        FaqItem item = new FaqItem();
        item.setQuestion(request.getQuestion().trim());
        item.setAnswer(request.getAnswer().trim());
        item.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        item.setActive(request.getActive() != null ? request.getActive() : true);

        faqRepository.save(item);
        return ResponseEntity.ok(toDto(item));
    }

    // ── Editar FAQ existente ──────────────────────────────────────────────────
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody FaqItemRequest request) {
        FaqItem item = faqRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("FAQ no encontrada"));

        if (request.getQuestion() != null && !request.getQuestion().isBlank()) {
            item.setQuestion(request.getQuestion().trim());
        }
        if (request.getAnswer() != null && !request.getAnswer().isBlank()) {
            item.setAnswer(request.getAnswer().trim());
        }
        if (request.getDisplayOrder() != null) {
            item.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }

        faqRepository.save(item);
        return ResponseEntity.ok(toDto(item));
    }

    // ── Eliminar FAQ ──────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id) {
        FaqItem item = faqRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("FAQ no encontrada"));

        faqRepository.delete(item);
        return ResponseEntity.ok("Pregunta eliminada.");
    }

    // ── Mapper ────────────────────────────────────────────────────────────────
    private FaqItemDto toDto(FaqItem f) {
        return new FaqItemDto(f.getId(), f.getQuestion(), f.getAnswer(), f.getDisplayOrder());
    }
}
