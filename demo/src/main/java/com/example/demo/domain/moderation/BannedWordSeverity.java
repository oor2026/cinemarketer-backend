package com.example.demo.domain.moderation;

public enum BannedWordSeverity {
    BLOCK,   // Rechaza el comentario directamente
    REVIEW   // Lo guarda como PENDING_REVIEW para revisión humana
}
