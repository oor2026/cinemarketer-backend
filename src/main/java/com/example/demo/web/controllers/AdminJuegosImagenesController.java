package com.example.demo.web.controllers;

import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.application.services.JuegoImagenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Imágenes de las campañas (admin). Protegido por "/api/admin/**" (solo ADMIN).
 *   POST   /api/admin/juegos/campanias/{id}/imagenes/{tipo}    multipart, campo "image"
 *   DELETE /api/admin/juegos/campanias/{id}/imagenes/{tipo}
 * tipo: banner-desktop | banner-mobile | fondo-lateral | premio
 * Devuelve la campaña actualizada.
 */
@RestController
@RequestMapping("/api/admin/juegos/campanias/{id}/imagenes")
public class AdminJuegosImagenesController {

    private final JuegoImagenService service;

    public AdminJuegosImagenesController(JuegoImagenService service) {
        this.service = service;
    }

    @PostMapping("/{tipo}")
    public ResponseEntity<?> subir(@PathVariable Long id, @PathVariable String tipo,
                                   @RequestParam("image") MultipartFile archivo) {
        return ResponseEntity.ok(service.subir(id, tipo, archivo));
    }

    @DeleteMapping("/{tipo}")
    public ResponseEntity<?> quitar(@PathVariable Long id, @PathVariable String tipo) {
        return ResponseEntity.ok(service.quitar(id, tipo));
    }

    @ExceptionHandler(JuegoException.class)
    public ResponseEntity<?> error(JuegoException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
