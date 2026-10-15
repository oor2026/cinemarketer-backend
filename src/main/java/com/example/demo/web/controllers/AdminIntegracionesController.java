package com.example.demo.web.controllers;

import com.example.demo.domain.integracion.IntegracionEnlace;
import com.example.demo.domain.integracion.IntegracionEnlaceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Admin — Integraciones: enlaces de sorteos y promociones de cada cadena.
 * Protegido por la regla "/api/admin/**" (solo ADMIN) de SecurityConfig.
 *
 * GET    /api/admin/integraciones/enlaces?tipo=SORTEO
 * POST   /api/admin/integraciones/enlaces
 * PUT    /api/admin/integraciones/enlaces/{id}
 * PATCH  /api/admin/integraciones/enlaces/{id}/activo   { "activo": true }
 * DELETE /api/admin/integraciones/enlaces/{id}
 */
@RestController
@RequestMapping("/api/admin/integraciones/enlaces")
public class AdminIntegracionesController {

    private static final Set<String> TIPOS = Set.of(IntegracionEnlace.TIPO_SORTEO, IntegracionEnlace.TIPO_PROMO_CADENA);
    private static final Set<String> VINCULOS = Set.of(
            IntegracionEnlace.VINCULO_PUBLICO, IntegracionEnlace.VINCULO_CONTACTADO, IntegracionEnlace.VINCULO_ACUERDO);

    private final IntegracionEnlaceRepository repository;

    public AdminIntegracionesController(IntegracionEnlaceRepository repository) {
        this.repository = repository;
    }

    public record EnlaceRequest(String tipo, String cadena, String titulo, String url, Boolean activo,
                                Integer orden, String estadoVinculo, String notaInterna, LocalDate verificadoEn) {}

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam String tipo) {
        if (!TIPOS.contains(tipo)) return error("Tipo inválido");
        return ResponseEntity.ok(repository.findByTipoOrderByOrdenAscCadenaAsc(tipo));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody EnlaceRequest req) {
        String problema = validar(req);
        if (problema != null) return error(problema);
        IntegracionEnlace e = new IntegracionEnlace();
        aplicar(e, req);
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@PathVariable Long id, @RequestBody EnlaceRequest req) {
        String problema = validar(req);
        if (problema != null) return error(problema);
        return repository.findById(id)
                .<ResponseEntity<?>>map(e -> {
                    aplicar(e, req);
                    return ResponseEntity.ok(repository.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/activo")
    public ResponseEntity<?> cambiarActivo(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean activo = body.get("activo");
        if (activo == null) return error("Falta el campo activo");
        return repository.findById(id)
                .<ResponseEntity<?>>map(e -> {
                    e.setActivo(activo);
                    return ResponseEntity.ok(repository.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> borrar(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // El clic acumulado NO se toca al editar: solo lo suma el redireccionamiento.
    private void aplicar(IntegracionEnlace e, EnlaceRequest req) {
        e.setTipo(req.tipo());
        e.setCadena(req.cadena().trim());
        e.setTitulo(req.titulo() == null || req.titulo().isBlank() ? null : req.titulo().trim());
        e.setUrl(req.url().trim());
        e.setActivo(req.activo() != null && req.activo());
        e.setOrden(req.orden() == null ? 0 : req.orden());
        e.setEstadoVinculo(req.estadoVinculo() == null ? IntegracionEnlace.VINCULO_PUBLICO : req.estadoVinculo());
        e.setNotaInterna(req.notaInterna() == null || req.notaInterna().isBlank() ? null : req.notaInterna().trim());
        e.setVerificadoEn(req.verificadoEn());
    }

    private String validar(EnlaceRequest req) {
        if (req == null) return "Faltan los datos";
        if (req.tipo() == null || !TIPOS.contains(req.tipo())) return "Tipo inválido";
        if (req.cadena() == null || req.cadena().isBlank()) return "Falta la cadena";
        if (req.cadena().trim().length() > 100) return "El nombre de la cadena es demasiado largo";
        if (req.titulo() != null && req.titulo().trim().length() > 150) return "El título es demasiado largo";
        if (req.url() == null || req.url().isBlank()) return "Falta el enlace";
        String url = req.url().trim();
        if (url.length() > 500) return "El enlace es demasiado largo";
        // Solo enlaces web: el redireccionamiento manda al usuario a esta dirección.
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return "El enlace tiene que empezar con https://";
        try {
            java.net.URI.create(url);
        } catch (IllegalArgumentException ex) {
            return "El enlace no es válido (¿tiene espacios?)";
        }
        if (req.estadoVinculo() != null && !VINCULOS.contains(req.estadoVinculo())) return "Estado de vínculo inválido";
        if (req.notaInterna() != null && req.notaInterna().trim().length() > 1000) return "La nota interna es demasiado larga";
        return null;
    }

    private ResponseEntity<?> error(String mensaje) {
        return ResponseEntity.badRequest().body(Map.of("message", mensaje));
    }
}
