package com.example.demo.application.dtos;

import lombok.Data;

// ── Request: crear o editar FAQ ───────────────────────────────────────────────
@Data
public class FaqItemRequest {
    private String question;
    private String answer;
    private Integer displayOrder;
    private Boolean active;
}
