package com.example.demo.web.controllers;

import com.example.demo.application.services.AgendaDeCineBusquedaService;
import com.example.demo.application.services.AgendaDeCineScraperService;
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

    private final AgendaDeCineScraperService agendaDeCineScraperService;
    private final AgendaDeCineBusquedaService agendaDeCineBusquedaService;
    private final com.example.demo.application.services.CarteleraPreciosService carteleraPreciosService;
    private final RecomendacionEspirituService recomendacionEspirituService;
    private final UserRepository userRepository;

    public CarteleraController(AgendaDeCineScraperService agendaDeCineScraperService,
                               AgendaDeCineBusquedaService agendaDeCineBusquedaService,
                               com.example.demo.application.services.CarteleraPreciosService carteleraPreciosService,
                               RecomendacionEspirituService recomendacionEspirituService,
                               UserRepository userRepository) {
        this.agendaDeCineScraperService = agendaDeCineScraperService;
        this.agendaDeCineBusquedaService = agendaDeCineBusquedaService;
        this.carteleraPreciosService = carteleraPreciosService;
        this.recomendacionEspirituService = recomendacionEspirituService;
        this.userRepository = userRepository;
    }

    // GET /api/cartelera/peliculas — películas en cartelera (agendadecine.com/en-cartel).
    @GetMapping("/peliculas")
    public ResponseEntity<?> getPeliculasEnCartelera() {
        return ResponseEntity.ok(agendaDeCineScraperService.obtenerPeliculasEnCartelera());
    }

    // GET /api/cartelera/peliculas/{slug}/funciones — todas las funciones
    // reales de una película puntual (cine, día, horario, formato).
    @GetMapping("/peliculas/{slug}/funciones")
    public ResponseEntity<?> getFunciones(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(agendaDeCineScraperService.obtenerFunciones(slug));
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
            var zona = agendaDeCineScraperService.obtenerFuncionesPorPeliculaYGeografiaConZona(slug, provincia, localidad);
            return ResponseEntity.ok(zona.funciones().stream().map(f -> conPrecio(f, zona.zonaAmpliada())).toList());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No pudimos traer las funciones de esta película en esa zona"));
        }
    }

    // GET /api/cartelera/cines-cercanos — con lat+lng, ordena por distancia
    // real; con texto (sin lat/lng), filtra por nombre/ciudad/dirección;
    // sin ninguno de los dos, devuelve todos los cines activos.
    @GetMapping("/cines-cercanos")
    public ResponseEntity<?> getCinesCercanos(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String texto) {
        if (lat != null && lng != null) {
            return ResponseEntity.ok(agendaDeCineBusquedaService.obtenerCinesCercanos(lat, lng));
        }
        return ResponseEntity.ok(agendaDeCineBusquedaService.obtenerCinesPorTexto(texto));
    }

    // GET /api/cartelera/cadenas — cadenas disponibles en el directorio.
    @GetMapping("/cadenas")
    public ResponseEntity<?> getCadenas() {
        // Todavía no están cargadas las funciones (recién arrancó el backend): no se sabe qué
        // cadenas tienen funciones, y mostrarlas todas sería incorrecto. Se responde 503 para que
        // el front siga mostrando "Cargando" y reintente en unos segundos.
        if (!agendaDeCineScraperService.cadenasListas()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "3")
                    .body(Map.of("estado", "cargando"));
        }
        return ResponseEntity.ok(agendaDeCineScraperService.obtenerCadenasDisponibles());
    }

    // GET /api/cartelera/pelicula-info?titulo=Guardianes del Museo 2 — datos de una película en
    // cartelera. Por ahora la sinopsis (null si agendadecine no la tiene).
    @GetMapping("/pelicula-info")
    public ResponseEntity<?> getPeliculaInfo(@RequestParam String titulo) {
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("titulo", titulo);
        info.put("sinopsis", agendaDeCineScraperService.obtenerSinopsis(titulo));
        return ResponseEntity.ok(info);
    }

    // GET /api/cartelera/funciones?cadena=Cinemark — todas las funciones
    // (de cualquier película) en cines de esa cadena.
    @GetMapping("/funciones")
    public ResponseEntity<?> getFuncionesPorCadena(@RequestParam String cadena) {
        return ResponseEntity.ok(agendaDeCineScraperService.obtenerFuncionesPorCadena(cadena)
                .stream().map(this::conPrecio).toList());
    }

    // Sucursal de una cadena tal como la espera el front (campo "nombre"). Se
    // devuelve esto y no la entidad Cinema: el front lee "nombre", no "name", y
    // la entidad trae colecciones (imágenes, sorteos) que se cargaban con una
    // consulta por cine.
    public record CineDeCadena(Long id, String nombre, String nombreVisible, String cadena, String ciudad, String provincia, String direccion) {}

    // GET /api/cartelera/cadenas/{cadena}/cines — sucursales de una cadena, por nombre.
    @GetMapping("/cadenas/{cadena}/cines")
    public ResponseEntity<?> getCinesPorCadena(@PathVariable String cadena) {
        var cines = agendaDeCineScraperService.obtenerCinesPorCadena(cadena).stream()
                .map(c -> new CineDeCadena(c.getId(), c.getName(), agendaDeCineScraperService.nombreVisibleDe(c), agendaDeCineScraperService.cadenaDe(c), c.getCity(), c.getProvince(), c.getAddress()))
                .sorted(java.util.Comparator.comparing(CineDeCadena::nombre, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        return ResponseEntity.ok(cines);
    }

    // GET /api/cartelera/funciones-por-cine?cine=NombreExacto&cineId=123 — funciones de una
    // sucursal puntual (de cualquier película). Con cineId se cruzan por id de complejo; sin él,
    // por nombre.
    @GetMapping("/funciones-por-cine")
    public ResponseEntity<?> getFuncionesPorCine(@RequestParam String cine,
                                                 @RequestParam(required = false) Long cineId) {
        return ResponseEntity.ok(agendaDeCineScraperService.obtenerFuncionesPorCine(cine, cineId)
                .stream().map(this::conPrecio).toList());
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
            var zona = agendaDeCineScraperService.organizarSalidaConZona(provincia, localidad, fechaParsed, horario);

            var resultado = zona.funciones().stream().map(f -> conPrecio(f, zona.zonaAmpliada())).toList();

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No pudimos armar los resultados"));
        }
    }

    // Una función con todo lo que el front necesita para mostrarla: sus datos de
    // siempre más el precio de ESA función según su día y horario. Lo usan todos
    // los endpoints que devuelven funciones, así que cada atajo de Cartelera
    // recibe lo mismo.
    // - precioReferencia: lo que cuesta la entrada ese día; null si no hay precio.
    // - precioRegular: la tarifa regular de ese día.
    // - tarifaReducida: el precio de referencia es una tarifa reducida.
    // - promocion: "2X1" cuando esa tarifa es un 2x1 (el precio es por entrada,
    //   pero comprando de a dos); null en cualquier otro caso.
    // - tarifaDescripcion: cuándo rige esa tarifa ("Lun a mié"); null si no hay.
    // - precioOnline: si comprando por web o boletería electrónica sale menos,
    //   ese precio; null si no.
    // - precioDesde: la sucursal tiene salas premium (Premier, Comfort, Atmos...)
    //   que la función no distingue, así que el precio es el de la sala base.
    // - zonaAmpliada: en la localidad pedida no había funciones y se muestran las
    //   de toda la provincia (en CABA, de toda la ciudad). Es igual para todas
    //   las funciones de una misma respuesta.
    private Map<String, Object> conPrecio(AgendaDeCineScraperService.FuncionScrapeada f) {
        return conPrecio(f, false);
    }

    private Map<String, Object> conPrecio(AgendaDeCineScraperService.FuncionScrapeada f, boolean zonaAmpliada) {
        Map<String, Object> dto = new java.util.HashMap<>();
        dto.put("peliculaTitulo", f.peliculaTitulo());
        dto.put("provincia", f.provincia());
        dto.put("cineNombre", f.cineNombre());
        dto.put("dia", f.dia());
        dto.put("esHoy", f.esHoy());
        dto.put("horario", f.horario());
        dto.put("formato", f.formato());
        dto.put("idioma", f.idioma());
        dto.put("poster", f.poster());

        var precio = carteleraPreciosService.obtenerPrecioDelDia(f.cineNombre(), f.formato(), f.dia(), f.horario());
        dto.put("precioReferencia", precio == null ? null : precio.precio());
        dto.put("precioRegular", precio == null ? null : precio.precioRegular());
        dto.put("tarifaReducida", precio != null && precio.tarifaReducida());
        dto.put("promocion", precio != null && precio.dosPorUno() ? "2X1" : null);
        dto.put("tarifaDescripcion", precio == null ? null : precio.tarifaDescripcion());
        dto.put("precioOnline", precio == null ? null : precio.precioOnline());
        dto.put("precioDesde", precio != null && precio.precioDesde());
        dto.put("zonaAmpliada", zonaAmpliada);
        return dto;
    }

    // GET /api/cartelera/diagnostico-pelicula?slug=digger&provincia=caba&localidad=Belgrano — TEMPORAL:
    // sigue paso a paso la búsqueda de "Ya sé qué quiero ver" y dice dónde se pierden las funciones.
    // Borrar cuando se termine la revisión.
    @GetMapping("/diagnostico-pelicula")
    public ResponseEntity<?> diagnosticoPelicula(@RequestParam String slug,
                                                 @RequestParam String provincia,
                                                 @RequestParam(required = false) String localidad) {
        return ResponseEntity.ok(agendaDeCineScraperService.diagnosticoDePelicula(slug, provincia, localidad));
    }

    // GET /api/cartelera/precio-referencia?cine=Cinemark Palermo&formato=2D
    // Sin fecha: devuelve siempre la tarifa regular. Para el precio de una
    // función puntual, ver precioReferencia en /organizar-salida.
    // GET /api/cartelera/diagnostico-funciones — TEMPORAL: de qué cines del directorio hay
    // funciones cargadas ahora, por cadena. Borrar cuando se termine la revisión de cadenas.
    @GetMapping("/diagnostico-funciones")
    public ResponseEntity<?> diagnosticoFunciones() {
        return ResponseEntity.ok(agendaDeCineScraperService.diagnosticoDeFunciones());
    }

    @GetMapping("/precio-referencia")
    public ResponseEntity<?> getPrecioReferencia(@RequestParam String cine, @RequestParam String formato) {
        Double precio = carteleraPreciosService.obtenerPrecioReferencia(cine, formato);
        if (precio == null) {
            return ResponseEntity.ok(Map.of("disponible", false));
        }
        Map<String, Object> respuesta = new java.util.HashMap<>();
        respuesta.put("disponible", true);
        respuesta.put("precio", precio);
        // Si el precio de este cine está cargado a mano, se informa desde
        // qué fecha rige (ej. la del PDF de Cines Dino).
        String fechaManual = carteleraPreciosService.obtenerFechaPrecioManual(cine);
        if (fechaManual != null) {
            respuesta.put("actualizadoAl", fechaManual);
        }
        return ResponseEntity.ok(respuesta);
    }

    // GET /api/cartelera/recomendacion-espiritu — "¿Alguna recomendación?"
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