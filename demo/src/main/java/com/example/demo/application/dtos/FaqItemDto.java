package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// ── Response: FAQ para el usuario ────────────────────────────────────────────
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaqItemDto {
    private Long id;
    private String question;
    private String answer;
    private Integer displayOrder;
}
