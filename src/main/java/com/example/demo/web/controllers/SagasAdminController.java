package com.example.demo.web.controllers;

import com.example.demo.application.services.SagasSuspensionService;
import com.example.demo.application.services.SagasUniverso;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Herramientas del admin para las sagas (solo ADMIN):
 *   GET  /api/admin/sagas/universo    → cuántas películas tiene guardadas cada saga de sagas.json (para comprobar que el catálogo las armó)
 *   GET  /api/admin/sagas/pendientes  → vista previa: qué sellos de saga se suspenderían (no cambia nada)
 *   POST /api/admin/sagas/suspender   → los suspende
 */
@RestController
@RequestMapping("/api/admin/sagas")
@PreAuthorize("hasAuthority('ADMIN')")
public class SagasAdminController {

    private final SagasUniverso universo;
    private final SagasSuspensionService suspension;

    public SagasAdminController(SagasUniverso universo, SagasSuspensionService suspension) {
        this.universo = universo;
        this.suspension = suspension;
    }

    @GetMapping("/universo")
    public ResponseEntity<Object> universo() { return ResponseEntity.ok(universo.resumen()); }

    @GetMapping("/pendientes")
    public ResponseEntity<Map<String, Object>> pendientes() { return ResponseEntity.ok(suspension.vistaPrevia()); }

    @PostMapping("/suspender")
    public ResponseEntity<Map<String, Object>> suspender() { return ResponseEntity.ok(suspension.aplicar()); }
}
