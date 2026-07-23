package com.example.demo.domain.publication;

public enum PublicationModerationStatus {
    APPROVED,
    PENDING_REVIEW,
    REJECTED,
    // Exclusivo del pipeline de video: el archivo todavía se está encodeando
    // en Cloudflare Stream y aún no hay resultado de moderación disponible.
    PROCESSING
}