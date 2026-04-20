package com.example.demo.web.controllers;

import com.example.demo.application.dtos.FaqItemDto;
import com.example.demo.domain.faq.FaqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/faq")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:63342")
public class FaqController {

    private final FaqRepository faqRepository;

    // ── Listar FAQs activas (público, sin auth) ───────────────────────────────
    @GetMapping
    public ResponseEntity<List<FaqItemDto>> getFaqs() {
        List<FaqItemDto> result = faqRepository
                .findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(f -> new FaqItemDto(f.getId(), f.getQuestion(), f.getAnswer(), f.getDisplayOrder()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
