package com.example.demo.web.controllers;

import com.example.demo.domain.integracion.IntegracionEnlace;
import com.example.demo.domain.integracion.IntegracionEnlaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Integraciones — lado público (atajos "Sorteos" y "Promos y bancos" de Cartelera).
 * Quedan sin login por la regla "/api/public/**" que ya existe en SecurityConfig:
 * el redireccionamiento se abre en una pestaña nueva, que no lleva el token.
 *
 * GET /api/public/integraciones/enlaces?tipo=SORTEO — enlaces activos (sin datos internos)
 * GET /api/public/integraciones/ir/{id}            — suma un clic y redirige al sitio de la cadena
 */
@RestController
@RequestMapping("/api/public/integraciones")
public class IntegracionesPublicController {

    private static final String SITIO = "https://cinemarketer.com.ar";

    private final IntegracionEnlaceRepository repository;

    public IntegracionesPublicController(IntegracionEnlaceRepository repository) {
        this.repository = repository;
    }

    // Lo que ve el usuario: nada de notas internas, estado del vínculo ni clics.
    public record EnlacePublico(Long id, String cadena, String titulo) {}

    @GetMapping("/enlaces")
    public ResponseEntity<List<EnlacePublico>> enlaces(@RequestParam String tipo) {
        List<EnlacePublico> lista = repository.findByTipoAndActivoTrueOrderByOrdenAscCadenaAsc(tipo).stream()
                .map(e -> new EnlacePublico(e.getId(), e.getCadena(), e.getTitulo()))
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/ir/{id}")
    public ResponseEntity<Void> ir(@PathVariable Long id) {
        IntegracionEnlace e = repository.findById(id).orElse(null);
        // Desactivado o inexistente: a Cinemarketer, nunca a un enlace dado de baja.
        if (e == null || !Boolean.TRUE.equals(e.getActivo())) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(SITIO)).build();
        }
        repository.sumarClic(id);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(e.getUrl())).build();
    }
}
