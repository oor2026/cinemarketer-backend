package com.example.demo.web.controllers;

import com.example.demo.application.services.CarteleraLiveScraperService;
import com.example.demo.application.services.RecomendacionEspirituService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cartelera")
public class CarteleraController {

    private final CarteleraLiveScraperService carteleraLiveScraperService;
    private final com.example.demo.application.services.CarteleraPreciosService carteleraPreciosService;
    private final RecomendacionEspirituService recomendacionEspirituService;
    private final UserRepository userRepository;

    public CarteleraController(CarteleraLiveScraperService carteleraLiveScraperService,
                               com.example.demo.application.services.CarteleraPreciosService carteleraPreciosService,
                               RecomendacionEspirituService recomendacionEspirituService,
                               UserRepository userRepository) {
        this.carteleraLiveScraperService = carteleraLiveScraperService;
        this.carteleraPreciosService = carteleraPreciosService;
        this.recomendacionEspirituService = recomendacionEspirituService;
        this.userRepository = userRepository;
    }

    // GET /api/cartelera/peliculas — películas descubiertas en los cines
    // semilla (ver comentario en el service sobre el alcance actual).
    @GetMapping("/peliculas")
    public ResponseEntity<?> getPeliculasEnCartelera() {
        return ResponseEntity.ok(carteleraLiveScraperService.obtenerPeliculasEnCartelera());
    }

    // GET /api/cartelera/peliculas/{slug}/funciones — todas las funciones
    // reales de una película puntual (cine, día, horario, formato).
    @GetMapping("/peliculas/{slug}/funciones")
    public ResponseEntity<?> getFunciones(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(carteleraLiveScraperService.obtenerFunciones(slug));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No pudimos traer las funciones de esta película"));
        }
    }

    // GET /api/cartelera/peliculas/{slug}/funciones-por-geografia?provincia=X&localidad=Y
    // Mismas funciones que el endpoint anterior, pero acotadas a una
    // provincia/localidad — el paso "¿Dónde estás?" que se agrega antes
    // de mostrar resultados, tanto viniendo de "Ya sé qué quiero ver"
    // como de "¿Alguna recomendación?".
    @GetMapping("/peliculas/{slug}/funciones-por-geografia")
    public ResponseEntity<?> getFuncionesPorGeografia(
            @PathVariable String slug,
            @RequestParam String provincia,
            @RequestParam(required = false) String localidad) {
        try {
            return ResponseEntity.ok(carteleraLiveScraperService.obtenerFuncionesPorPeliculaYGeografia(slug, provincia, localidad));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No pudimos traer las funciones de esta película en esa zona"));
        }
    }

    // GET /api/cartelera/cines-cercanos — con lat+lng, ordena por distancia
    // real; con texto (sin lat/lng), filtra por nombre del cine como
    // fallback; sin ninguno de los dos, devuelve todos sin ordenar.
    @GetMapping("/cines-cercanos")
    public ResponseEntity<?> getCinesCercanos(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String texto) {
        if (lat != null && lng != null) {
            return ResponseEntity.ok(carteleraLiveScraperService.obtenerCinesCercanos(lat, lng));
        }
        return ResponseEntity.ok(carteleraLiveScraperService.obtenerCinesPorTexto(texto));
    }

    // GET /api/cartelera/cadenas — cadenas disponibles en los cines semilla.
    @GetMapping("/cadenas")
    public ResponseEntity<?> getCadenas() {
        return ResponseEntity.ok(carteleraLiveScraperService.obtenerCadenasDisponibles());
    }

    // GET /api/cartelera/funciones?cadena=Cinemark — todas las funciones
    // (de cualquier película) en cines de esa cadena.
    @GetMapping("/funciones")
    public ResponseEntity<?> getFuncionesPorCadena(@RequestParam String cadena) {
        return ResponseEntity.ok(carteleraLiveScraperService.obtenerFuncionesPorCadena(cadena));
    }

    // GET /api/cartelera/cadenas/{cadena}/cines — sucursales de una cadena.
    @GetMapping("/cadenas/{cadena}/cines")
    public ResponseEntity<?> getCinesPorCadena(@PathVariable String cadena) {
        return ResponseEntity.ok(carteleraLiveScraperService.obtenerCinesPorCadena(cadena));
    }

    // GET /api/cartelera/funciones-por-cine?cine=NombreExacto — funciones
    // de una sucursal puntual (de cualquier película).
    @GetMapping("/funciones-por-cine")
    public ResponseEntity<?> getFuncionesPorCine(@RequestParam String cine) {
        return ResponseEntity.ok(carteleraLiveScraperService.obtenerFuncionesPorCine(cine));
    }

    // GET /api/cartelera/organizar-salida?provincia=X&localidad=Y&fecha=YYYY-MM-DD&horario=manana|tarde|noche
    @GetMapping("/organizar-salida")
    public ResponseEntity<?> organizarSalida(
            @RequestParam String provincia,
            @RequestParam(required = false) String localidad,
            @RequestParam String fecha,
            @RequestParam(required = false) String horario) {
        try {
            java.time.LocalDate fechaParsed = java.time.LocalDate.parse(fecha);
            var funciones = carteleraLiveScraperService.organizarSalida(provincia, localidad, fechaParsed, horario);

            var resultado = funciones.stream().map(f -> {
                Map<String, Object> dto = new java.util.HashMap<>();
                dto.put("peliculaTitulo", f.peliculaTitulo());
                dto.put("cineNombre", f.cineNombre());
                dto.put("dia", f.dia());
                dto.put("esHoy", f.esHoy());
                dto.put("horario", f.horario());
                dto.put("formato", f.formato());
                dto.put("idioma", f.idioma());
                dto.put("poster", f.poster());
                dto.put("precioReferencia", carteleraPreciosService.obtenerPrecioReferencia(f.cineNombre(), f.formato()));
                return dto;
            }).toList();

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No pudimos armar los resultados"));
        }
    }

    // GET /api/cartelera/precio-referencia?cine=Cinemark Palermo&formato=2D
    @GetMapping("/precio-referencia")
    public ResponseEntity<?> getPrecioReferencia(@RequestParam String cine, @RequestParam String formato) {
        Double precio = carteleraPreciosService.obtenerPrecioReferencia(cine, formato);
        if (precio == null) {
            return ResponseEntity.ok(Map.of("disponible", false));
        }
        Map<String, Object> respuesta = new java.util.HashMap<>();
        respuesta.put("disponible", true);
        respuesta.put("precio", precio);
        // Dinosaurio es el único caso con fecha de referencia (precio
        // cargado a mano, no scrapeado en vivo) — se agrega si aplica.
        String fechaDino = carteleraPreciosService.obtenerFechaActualizacionDinosaurio();
        if (fechaDino != null && cine.toLowerCase().contains("dinosaurio")) {
            respuesta.put("actualizadoAl", fechaDino);
        }
        return ResponseEntity.ok(respuesta);
    }

    // GET /api/cartelera/recomendacion-espiritu — "¿Alguna recomendación?"
    // Cruza el top-3 de género del espíritu cinéfilo del usuario contra
    // las películas en cartelera. Sin filtro geográfico a propósito — es
    // una recomendación, no un buscador; el usuario ve dónde/cuándo se da
    // cada una recién al entrar a esa película puntual (mismo endpoint de
    // funciones que ya usa "Ya sé qué quiero ver").
    @GetMapping("/recomendacion-espiritu")
    public ResponseEntity<?> getRecomendacionEspiritu(Authentication authentication) {
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            return ResponseEntity.ok(recomendacionEspirituService.recomendar(user.getId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No pudimos armar tu recomendación"));
        }
    }
}
