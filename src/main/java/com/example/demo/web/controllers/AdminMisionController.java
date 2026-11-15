package com.example.demo.web.controllers;

import com.example.demo.application.services.CatalogoActualizacionService;
import com.example.demo.application.services.CloudinaryService;
import com.example.demo.application.services.MisionAdminService;
import com.example.demo.application.services.MisionAdminService.AdminException;
import com.example.demo.domain.mision.BuscadorTmdb;
import com.example.demo.domain.mision.BuscadorTmdb.Opcion;
import com.example.demo.domain.mision.MisionAdminDatos.Filtros;
import com.example.demo.domain.mision.MisionAdminForm;
import com.example.demo.domain.mision.MisionAdminFormParser;
import com.example.demo.domain.mision.MisionAdminFormParser.Parseo;
import com.example.demo.domain.mision.ValidadorImagenSello;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * El panel de admin de Misiones y Sellos. Es solo el pegamento: lee lo que manda el panel, llama al servicio (MisionAdminService, que tiene todas las
 * reglas) y devuelve el resultado con el código HTTP que corresponde. Todo exige la autoridad ADMIN.
 * Errores: 422 = hay datos para corregir (con la lista de problemas por campo) · 404 = no existe · 409 = no se puede por el estado actual · 502 = falló TMDB o Cloudinary.
 */
@RestController
@RequestMapping("/api/admin/misiones")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminMisionController {

    private static final Logger log = LoggerFactory.getLogger(AdminMisionController.class);
    private static final String CARPETA_IMAGENES = "misiones/sellos";

    private final MisionAdminService servicio;
    private final BuscadorTmdb buscador;
    private final CatalogoActualizacionService actualizacion;
    private final CloudinaryService cloudinary;
    private final UserRepository userRepository;

    public AdminMisionController(MisionAdminService servicio, BuscadorTmdb buscador, CatalogoActualizacionService actualizacion,
                                 CloudinaryService cloudinary, UserRepository userRepository) {
        this.servicio = servicio;
        this.buscador = buscador;
        this.actualizacion = actualizacion;
        this.cloudinary = cloudinary;
        this.userRepository = userRepository;
    }

    // ================================================================================================ lo que el asistente necesita
    /** GET /api/admin/misiones/opciones — qué tipos de misión existen y qué admite cada uno (el asistente se arma con esto). */
    @GetMapping("/opciones")
    public ResponseEntity<?> opciones() { return leer(servicio::opciones); }

    /** GET /api/admin/misiones/sellos — los sellos existentes, para reutilizar uno como un nivel más. */
    @GetMapping("/sellos")
    public ResponseEntity<?> sellos() { return leer(servicio::sellos); }

    /** POST /api/admin/misiones/validar — errores y avisos mientras el admin completa el asistente. No guarda nada. Body: el formulario (+ misionId si se está editando). */
    @PostMapping("/validar")
    public ResponseEntity<?> validar(@RequestBody(required = false) Map<String, Object> body) {
        Parseo p = MisionAdminFormParser.parsear(body);
        if (!p.ok()) return ResponseEntity.ok(resultado(p));                            // lo que no se pudo leer, se marca igual que cualquier otro error
        return leer(() -> servicio.validar(p.form(), numero(body == null ? null : body.get("misionId"))));
    }

    // ================================================================================================ misiones
    /** GET /api/admin/misiones?q=&estado=&origen=&categoria=&metrica=&vigencia= */
    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String q, @RequestParam(required = false) String estado, @RequestParam(required = false) String origen,
                                    @RequestParam(required = false) String categoria, @RequestParam(required = false) String metrica, @RequestParam(required = false) String vigencia) {
        return leer(() -> servicio.listar(new Filtros(q, estado, origen, categoria, metrica, vigencia)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) { return leer(() -> servicio.detalle(id)); }

    @GetMapping("/{id}/historial")
    public ResponseEntity<?> historial(@PathVariable Long id) { return leer(() -> servicio.historial(id)); }

    /** POST /api/admin/misiones — crea la misión como BORRADOR. */
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody(required = false) Map<String, Object> body, @AuthenticationPrincipal UserDetails ud) {
        return escribir(ud, HttpStatus.CREATED, admin -> servicio.crear(formulario(body), admin));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body, @AuthenticationPrincipal UserDetails ud) {
        return escribir(ud, HttpStatus.OK, admin -> servicio.editar(id, formulario(body), admin));
    }

    @PostMapping("/{id}/publicar")
    public ResponseEntity<?> publicar(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) { return escribir(ud, HttpStatus.OK, admin -> servicio.publicar(id, admin)); }

    @PostMapping("/{id}/pausar")
    public ResponseEntity<?> pausar(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) { return escribir(ud, HttpStatus.OK, admin -> servicio.pausar(id, admin)); }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<?> duplicar(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) { return escribir(ud, HttpStatus.CREATED, admin -> servicio.duplicar(id, admin)); }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return escribir(ud, HttpStatus.OK, admin -> { servicio.eliminar(id, admin); return Map.of("ok", true); });
    }

    // ================================================================================================ imagen del sello
    /**
     * POST /api/admin/misiones/imagen (multipart, campo «file») — sube la imagen del sello a Cloudinary (carpeta misiones/sellos) y devuelve su URL, que después se manda
     * en «selloImagenUrl» al crear o editar la misión. Antes de subirla se controla el formato real, el peso y que sea cuadrada.
     */
    @PostMapping(value = "/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirImagen(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Elegí una imagen para subir."));
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { return ResponseEntity.badRequest().body(Map.of("error", "No se pudo leer el archivo.")); }
        ValidadorImagenSello.Resultado r = ValidadorImagenSello.validar(bytes, file.getContentType());
        if (!r.ok()) return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", r.error()));
        try {
            String url = cloudinary.uploadImage(file, CARPETA_IMAGENES);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("url", url); ok.put("tipo", r.tipo()); ok.put("ancho", r.ancho()); ok.put("alto", r.alto()); ok.put("bytes", bytes.length);
            return ResponseEntity.ok(ok);
        } catch (IOException | RuntimeException e) {
            log.error("[admin-misiones] no se pudo subir la imagen del sello: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "No pudimos subir la imagen. Probá de nuevo en un momento."));
        }
    }

    // ================================================================================================ buscadores de TMDB
    @GetMapping("/tmdb/generos")
    public ResponseEntity<?> generos() { return tmdb(() -> opciones(buscador.generos())); }

    @GetMapping("/tmdb/personas")
    public ResponseEntity<?> personas(@RequestParam(required = false) String q) { return tmdb(() -> opciones(buscador.personas(q))); }

    @GetMapping("/tmdb/sagas")
    public ResponseEntity<?> sagas(@RequestParam(required = false) String q) { return tmdb(() -> opciones(buscador.sagas(q))); }

    /** GET /api/admin/misiones/tmdb/nombres?generoId=&directorId=&actorId=&sagaId= — el nombre de lo que ya está elegido (para mostrarlo al editar). */
    @GetMapping("/tmdb/nombres")
    public ResponseEntity<?> nombres(@RequestParam(required = false) Long generoId, @RequestParam(required = false) Long directorId,
                                     @RequestParam(required = false) Long actorId, @RequestParam(required = false) Long sagaId) {
        return tmdb(() -> {
            Map<String, Object> r = new LinkedHashMap<>();
            if (generoId != null) r.put("genero", opcion(() -> buscador.genero(generoId)));
            if (directorId != null) r.put("director", opcion(() -> buscador.persona(directorId)));
            if (actorId != null) r.put("actor", opcion(() -> buscador.persona(actorId)));
            if (sagaId != null) r.put("saga", opcion(() -> buscador.saga(sagaId)));
            return r;
        });
    }

    // ================================================================================================ catálogo automático
    /** GET /api/admin/misiones/catalogo?familia=&q=&estado=  (estado: ACTIVO, EXCLUIDO o RETIRADO) */
    @GetMapping("/catalogo")
    public ResponseEntity<?> catalogo(@RequestParam(required = false) String familia, @RequestParam(required = false) String q, @RequestParam(required = false) String estado) {
        return leer(() -> servicio.catalogo(familia, q, estado));
    }

    /** POST /api/admin/misiones/catalogo/{id}/excluir — body opcional: {"retirar": true} para además sacársela a quienes ya la tienen asignada. */
    @PostMapping("/catalogo/{id}/excluir")
    public ResponseEntity<?> excluir(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body, @AuthenticationPrincipal UserDetails ud) {
        boolean retirar = body != null && Boolean.parseBoolean(String.valueOf(body.get("retirar")));
        return escribir(ud, HttpStatus.OK, admin -> { servicio.cambiarExclusion(id, true, retirar, admin); return Map.of("ok", true); });
    }

    @PostMapping("/catalogo/{id}/restaurar")
    public ResponseEntity<?> restaurar(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        return escribir(ud, HttpStatus.OK, admin -> { servicio.cambiarExclusion(id, false, false, admin); return Map.of("ok", true); });
    }

    /** POST /api/admin/misiones/catalogo/actualizar — renueva el catálogo desde TMDB en segundo plano (tarda un par de minutos). 202 si arrancó, 409 si ya hay una en marcha. */
    @PostMapping("/catalogo/actualizar")
    public ResponseEntity<?> actualizar(@AuthenticationPrincipal UserDetails ud) {
        Long admin = adminId(ud);
        if (admin == null) return noIdentificado();
        if (!actualizacion.iniciar(admin)) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Ya hay una actualización en curso: esperá a que termine."));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("iniciada", true));
    }

    /** GET /api/admin/misiones/catalogo/estado — si está corriendo y cómo terminó la última. */
    @GetMapping("/catalogo/estado")
    public ResponseEntity<?> estadoActualizacion() {
        CatalogoActualizacionService.Estado e = actualizacion.estado();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("corriendo", e.corriendo()); m.put("iniciadaPor", e.iniciadaPor()); m.put("inicio", e.inicio()); m.put("fin", e.fin()); m.put("resultado", e.resultado()); m.put("errores", e.errores());
        return ResponseEntity.ok(m);
    }

    // ================================================================================================ piezas
    private Long adminId(UserDetails ud) {
        if (ud == null) return null;
        return userRepository.findByEmail(ud.getUsername()).map(User::getId).orElse(null);
    }

    private static ResponseEntity<?> noIdentificado() { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No se pudo identificar al administrador.")); }

    /** Lo que solo lee: no necesita saber quién es el admin. */
    private ResponseEntity<?> leer(Supplier<Object> accion) {
        try { return ResponseEntity.ok(accion.get()); } catch (AdminException e) { return error(e); }
    }

    /** Lo que escribe: necesita el id del admin (queda registrado en el historial). */
    private ResponseEntity<?> escribir(UserDetails ud, HttpStatus exito, Function<Long, Object> accion) {
        Long admin = adminId(ud);
        if (admin == null) return noIdentificado();
        try { return ResponseEntity.status(exito).body(accion.apply(admin)); } catch (AdminException e) { return error(e); }
    }

    private static ResponseEntity<?> error(AdminException e) { return ResponseEntity.status(e.http()).body(MisionAdminService.errorMapa(e)); }

    /** Lee el formulario; si algo no se puede leer, sale como cualquier otro error de validación (422, por campo). */
    private static MisionAdminForm formulario(Map<String, Object> body) {
        Parseo p = MisionAdminFormParser.parsear(body);
        if (!p.ok()) throw new AdminException(422, "Revisá los datos de la misión.", p.problemas(), List.of());
        return p.form();
    }

    private static Map<String, Object> resultado(Parseo p) {
        List<Map<String, Object>> errores = new ArrayList<>();
        p.problemas().forEach(x -> errores.add(Map.of("campo", x.campo(), "mensaje", x.mensaje())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false); m.put("errores", errores); m.put("avisos", List.of());
        return m;
    }

    private static Long numero(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return o == null || o.toString().isBlank() ? null : Long.parseLong(o.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private ResponseEntity<?> tmdb(Supplier<Object> accion) {
        try { return ResponseEntity.ok(accion.get()); }
        catch (RuntimeException e) {
            log.warn("[admin-misiones] TMDB no respondió: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "No pudimos consultar TMDB. Probá de nuevo en un momento."));
        }
    }

    private static List<Map<String, Object>> opciones(List<Opcion> l) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Opcion o : l) r.add(mapa(o));
        return r;
    }

    /** null si no existe o si TMDB no responde por ese pedido (el resto de los nombres se muestran igual). */
    private static Map<String, Object> opcion(Supplier<Opcion> s) {
        try { Opcion o = s.get(); return o == null ? null : mapa(o); } catch (RuntimeException e) { return null; }
    }

    private static Map<String, Object> mapa(Opcion o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id()); m.put("nombre", o.nombre()); m.put("imagen", o.imagen()); m.put("detalle", o.detalle());
        return m;
    }
}
