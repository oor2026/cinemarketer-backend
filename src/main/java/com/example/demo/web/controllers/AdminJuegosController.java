package com.example.demo.web.controllers;

import com.example.demo.application.services.JuegoAdminService;
import com.example.demo.application.services.JuegoCierreService;
import com.example.demo.application.services.JuegoCierreService.DescalificarRequest;
import com.example.demo.application.services.JuegoCierreService.MotivoRequest;
import com.example.demo.application.services.JuegoAdminService.CampaniaRequest;
import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.application.services.JuegoAdminService.PreguntaRequest;
import com.example.demo.application.services.JuegoTmdbService;
import com.example.demo.application.services.JuegoTmdbService.GenerarRequest;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Plataforma de juegos (admin). Protegido por la regla "/api/admin/**" (solo
 * ADMIN) de SecurityConfig.
 *
 * Campañas
 *   GET    /api/admin/juegos/campanias
 *   POST   /api/admin/juegos/campanias
 *   GET    /api/admin/juegos/campanias/{id}            campaña + resumen de tandas
 *   PUT    /api/admin/juegos/campanias/{id}
 *   DELETE /api/admin/juegos/campanias/{id}            solo en borrador
 *   POST   /api/admin/juegos/campanias/{id}/publicar
 *   POST   /api/admin/juegos/campanias/{id}/volver-a-borrador
 *   POST   /api/admin/juegos/campanias/{id}/archivar
 *   PUT    /api/admin/juegos/campanias/{id}/landing-oculta   { "oculta": true } (solo archivadas)
 * Banco de preguntas
 *   GET    /api/admin/juegos/campanias/{id}/preguntas
 *   POST   /api/admin/juegos/campanias/{id}/preguntas
 *   GET    /api/admin/juegos/tmdb/buscar?tipo=PELICULA|SERIE|SAGA|PERSONA&q=aveng   buscador predictivo
 *   POST   /api/admin/juegos/campanias/{id}/preguntas/tmdb   { "temas": [{"tipo":"SAGA","id":86311}], "cantidad": 20 } → candidatas, sin guardar
 *   POST   /api/admin/juegos/campanias/{id}/preguntas/lote   [ …preguntas elegidas… ] → las guarda
 *   PUT    /api/admin/juegos/preguntas/{id}
 *   DELETE /api/admin/juegos/preguntas/{id}
 * Tandas diarias
 *   POST   /api/admin/juegos/campanias/{id}/tandas/automatico
 *   PUT    /api/admin/juegos/preguntas/{id}/dia         { "dia": "2026-12-03" } o { "dia": null }
 * Ranking y ganadores
 *   GET    /api/admin/juegos/campanias/{id}/cierre                 ranking completo + ganadores
 *   POST   /api/admin/juegos/campanias/{id}/descalificar           { "participanteId": 5, "motivo": "..." }
 *   DELETE /api/admin/juegos/campanias/{id}/descalificar/{participanteId}
 *   POST   /api/admin/juegos/campanias/{id}/ganadores/proponer
 *   POST   /api/admin/juegos/campanias/{id}/ganadores/publicar
 *   POST   /api/admin/juegos/ganadores/{id}/entregado
 *   POST   /api/admin/juegos/ganadores/{id}/pasar-al-suplente      { "motivo": "..." }
 */
@RestController
@RequestMapping("/api/admin/juegos")
public class AdminJuegosController {

    private final JuegoAdminService service;
    private final JuegoTmdbService tmdbService;
    private final JuegoCierreService cierreService;
    private final UserRepository userRepository;

    public AdminJuegosController(JuegoAdminService service, JuegoTmdbService tmdbService,
                                 JuegoCierreService cierreService, UserRepository userRepository) {
        this.service = service;
        this.tmdbService = tmdbService;
        this.cierreService = cierreService;
        this.userRepository = userRepository;
    }

    public record MoverRequest(LocalDate dia) {}

    // ---------- Campañas ----------

    @GetMapping("/campanias")
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping("/campanias")
    public ResponseEntity<?> crear(@RequestBody CampaniaRequest req) {
        return ResponseEntity.ok(service.crear(req));
    }

    @GetMapping("/campanias/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalle(id));
    }

    @PutMapping("/campanias/{id}")
    public ResponseEntity<?> editar(@PathVariable Long id, @RequestBody CampaniaRequest req) {
        return ResponseEntity.ok(service.editar(id, req));
    }

    @DeleteMapping("/campanias/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/campanias/{id}/publicar")
    public ResponseEntity<?> publicar(@PathVariable Long id) {
        return ResponseEntity.ok(service.publicar(id));
    }

    @PostMapping("/campanias/{id}/volver-a-borrador")
    public ResponseEntity<?> volverABorrador(@PathVariable Long id) {
        return ResponseEntity.ok(service.volverABorrador(id));
    }

    @PostMapping("/campanias/{id}/archivar")
    public ResponseEntity<?> archivar(@PathVariable Long id) {
        return ResponseEntity.ok(service.archivar(id));
    }

    public record LandingOcultaRequest(Boolean oculta) {}

    @PutMapping("/campanias/{id}/landing-oculta")
    public ResponseEntity<?> ocultarLanding(@PathVariable Long id, @RequestBody LandingOcultaRequest req) {
        return ResponseEntity.ok(service.ocultarLanding(id, req != null && Boolean.TRUE.equals(req.oculta())));
    }

    // ---------- Banco de preguntas ----------

    @GetMapping("/campanias/{id}/preguntas")
    public ResponseEntity<?> preguntas(@PathVariable Long id) {
        return ResponseEntity.ok(service.preguntas(id));
    }

    @PostMapping("/campanias/{id}/preguntas")
    public ResponseEntity<?> crearPregunta(@PathVariable Long id, @RequestBody PreguntaRequest req) {
        return ResponseEntity.ok(service.crearPregunta(id, req));
    }

    @GetMapping("/tmdb/buscar")
    public ResponseEntity<?> buscarEnTmdb(@RequestParam String tipo, @RequestParam String q) {
        return ResponseEntity.ok(tmdbService.buscar(tipo, q));
    }

    @PostMapping("/campanias/{id}/preguntas/tmdb")
    public ResponseEntity<?> generarDesdeTmdb(@PathVariable Long id, @RequestBody GenerarRequest req) {
        service.buscarParaGenerar(id);
        return ResponseEntity.ok(tmdbService.generar(id, req));
    }

    @PostMapping("/campanias/{id}/preguntas/lote")
    public ResponseEntity<?> crearPreguntasLote(@PathVariable Long id, @RequestBody List<PreguntaRequest> lote) {
        return ResponseEntity.ok(service.crearPreguntasLote(id, lote));
    }

    @PutMapping("/preguntas/{id}")
    public ResponseEntity<?> editarPregunta(@PathVariable Long id, @RequestBody PreguntaRequest req) {
        return ResponseEntity.ok(service.editarPregunta(id, req));
    }

    @DeleteMapping("/preguntas/{id}")
    public ResponseEntity<?> eliminarPregunta(@PathVariable Long id) {
        service.eliminarPregunta(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Tandas ----------

    @PostMapping("/campanias/{id}/tandas/automatico")
    public ResponseEntity<?> asignarAutomatico(@PathVariable Long id) {
        return ResponseEntity.ok(service.asignarAutomatico(id));
    }

    @PutMapping("/preguntas/{id}/dia")
    public ResponseEntity<?> moverPregunta(@PathVariable Long id, @RequestBody MoverRequest req) {
        return ResponseEntity.ok(service.moverPregunta(id, req == null ? null : req.dia()));
    }

    // ---------- Ranking y ganadores ----------

    @GetMapping("/campanias/{id}/cierre")
    public ResponseEntity<?> cierre(@PathVariable Long id) {
        return ResponseEntity.ok(cierreService.cierre(id));
    }

    @PostMapping("/campanias/{id}/descalificar")
    public ResponseEntity<?> descalificar(@PathVariable Long id, @RequestBody DescalificarRequest req,
                                          @AuthenticationPrincipal UserDetails admin) {
        Long adminId = admin == null ? null
                : userRepository.findByEmail(admin.getUsername()).map(u -> u.getId()).orElse(null);
        return ResponseEntity.ok(cierreService.descalificar(id, req, adminId));
    }

    @DeleteMapping("/campanias/{id}/descalificar/{participanteId}")
    public ResponseEntity<?> rehabilitar(@PathVariable Long id, @PathVariable Long participanteId) {
        return ResponseEntity.ok(cierreService.rehabilitar(id, participanteId));
    }

    @PostMapping("/campanias/{id}/ganadores/proponer")
    public ResponseEntity<?> proponerGanadores(@PathVariable Long id) {
        return ResponseEntity.ok(cierreService.proponer(id));
    }

    @PostMapping("/campanias/{id}/ganadores/publicar")
    public ResponseEntity<?> publicarGanadores(@PathVariable Long id) {
        return ResponseEntity.ok(cierreService.publicarGanadores(id));
    }

    @PostMapping("/ganadores/{id}/entregado")
    public ResponseEntity<?> marcarEntregado(@PathVariable Long id) {
        return ResponseEntity.ok(cierreService.marcarEntregado(id));
    }

    @PostMapping("/ganadores/{id}/pasar-al-suplente")
    public ResponseEntity<?> pasarAlSuplente(@PathVariable Long id, @RequestBody MotivoRequest req) {
        return ResponseEntity.ok(cierreService.pasarAlSuplente(id, req));
    }

    // Los errores del service llegan con su código (400, 404, 409) y un mensaje para mostrar.
    @ExceptionHandler(JuegoException.class)
    public ResponseEntity<?> error(JuegoException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
