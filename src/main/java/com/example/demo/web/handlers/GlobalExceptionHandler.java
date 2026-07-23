package com.example.demo.web.handlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Comentario idéntico al anterior — antispam
     */
    @ExceptionHandler(DuplicateCommentException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateComment(DuplicateCommentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", ex.getMessage(),
                        "code", "DUPLICATE_COMMENT"
                ));
    }

    /**
     * Puntos insuficientes para canjear
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";

        // Reporte duplicado → 409
        if (msg.contains("Ya reportaste")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", msg, "code", "DUPLICATE_REPORT"));
        }

        // Resto → 422
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", msg, "code", "ILLEGAL_STATE"));
    }

    /**
     * Entidad no encontrada
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", ex.getMessage(),
                        "code", "INTERNAL_ERROR"
                ));
    }
}