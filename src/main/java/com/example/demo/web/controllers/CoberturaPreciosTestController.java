package com.example.demo.web.controllers;

import com.example.demo.application.services.AgendaDeCineScraperService;
import com.example.demo.application.services.CarteleraArgentinaPreciosService;
import com.example.demo.application.services.CarteleraPreciosService;
import com.example.demo.domain.cinema.Cinema;
import com.example.demo.domain.cinema.CinemaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TEMPORAL — solo para diagnosticar la capa de precios. Mediciones
 * distintas, a propósito:
 *
 * (1) /cobertura-precios — mide contra NUESTRO directorio (cines
 * sincronizados desde agendadecine.com): de esos, ¿para cuántos
 * encontramos precio, sea por scraper de cadena o por el fallback de
 * carteleraargentina.com.ar? Mezcla fuente + matching de nombre en un
 * solo número.
 *
 * (2) /cobertura-cartelera-argentina — mide SOLO el fallback editorial de
 * carteleraargentina.com.ar: de todo lo que esa fuente lista en sus 15
 * provincias, ¿cuántos traen la sección "Precios" en su propia ficha?
 *
 * (3) /total-precios-combinados — NO matchea nada. Suma cruda de lo que
 * traen las DOS fuentes de precio por separado (los scrapers de cadena,
 * escopados por cadena para que una sucursal con el mismo nombre corto
 * en dos cadenas distintas no se pise + el fallback de
 * carteleraargentina.com.ar), para comparar ese total a ojo contra los
 * cines de agendadecine.com.
 *
 * (4) /precios-detallados-showcase — confirma que cachePreciosDetallados
 * (general/matinee/niños/promo) se llenó bien, único dato enriquecido
 * que hoy produce algún scraper (Showcase).
 *
 * (5) /precios-cache — vuelca cadena → sucursal → formato → precio, para
 * chequear valores contra las webs y no solo las claves.
 *
 * (6) /formatos-por-cadena — formatos que llegan desde agendadecine en
 * las funciones, por cadena, con su cantidad. Sirve para armar el cruce
 * de formatos con datos reales.
 *
 * (7) /precio-del-dia — precio de un cine y formato puntuales en una fecha
 * y hora dadas: confirma que la tarifa reducida (ej. lunes a miércoles
 * 50%) se aplica cuando corresponde.
 *
 * Borrar junto con el resto de los controllers de diagnóstico cuando ya
 * no haga falta.
 */
@RestController
public class CoberturaPreciosTestController {

    private final CinemaRepository cinemaRepository;
    private final CarteleraPreciosService carteleraPreciosService;
    private final CarteleraArgentinaPreciosService carteleraArgentinaPreciosService;
    private final AgendaDeCineScraperService agendaDeCineScraperService;

    public CoberturaPreciosTestController(CinemaRepository cinemaRepository,
                                          CarteleraPreciosService carteleraPreciosService,
                                          CarteleraArgentinaPreciosService carteleraArgentinaPreciosService,
                                          AgendaDeCineScraperService agendaDeCineScraperService) {
        this.cinemaRepository = cinemaRepository;
        this.carteleraPreciosService = carteleraPreciosService;
        this.carteleraArgentinaPreciosService = carteleraArgentinaPreciosService;
        this.agendaDeCineScraperService = agendaDeCineScraperService;
    }

    // GET /api/test/cobertura-precios — recorre TODOS los cines activos
    // de NUESTRO directorio y cuenta para cuántos obtenerPrecioReferencia()
    // devuelve algo (probando 2D y 3D, ya que algunas fuentes solo listan
    // uno de los dos). Incluye el detalle de cuáles NO tienen precio.
    @GetMapping("/api/test/cobertura-precios")
    public Map<String, Object> probarCoberturaPrecios() {
        List<Cinema> todos = cinemaRepository.findByActiveTrue();

        List<String> sinPrecio = new ArrayList<>();
        int conPrecio = 0;

        for (Cinema c : todos) {
            Double precio2d = carteleraPreciosService.obtenerPrecioReferencia(c.getName(), "2D");
            Double precio3d = carteleraPreciosService.obtenerPrecioReferencia(c.getName(), "3D");
            if (precio2d != null || precio3d != null) {
                conPrecio++;
            } else {
                sinPrecio.add(c.getName());
            }
        }

        return Map.of(
                "totalCines", todos.size(),
                "conPrecio", conPrecio,
                "sinPrecio", todos.size() - conPrecio,
                "porcentajeCobertura", Math.round((conPrecio * 10000.0) / todos.size()) / 100.0,
                "listaSinPrecio", sinPrecio
        );
    }

    // GET /api/test/cobertura-cartelera-argentina — mide la fuente SOLA,
    // sin matchear contra nuestro directorio: de todo lo que
    // carteleraargentina.com.ar lista en sus 15 provincias, ¿cuántos
    // traen la sección "Precios" en su propia ficha?
    @GetMapping("/api/test/cobertura-cartelera-argentina")
    public Map<String, Object> probarCoberturaCarteleraArgentina() {
        List<CarteleraArgentinaPreciosService.CineConUbicacion> todos =
                carteleraArgentinaPreciosService.obtenerCinesConUbicacion();
        Map<String, Map<String, Double>> precios =
                carteleraArgentinaPreciosService.obtenerPreciosPropiosDescubiertos();

        List<String> sinPrecio = todos.stream()
                .map(CarteleraArgentinaPreciosService.CineConUbicacion::nombre)
                .filter(nombre -> !precios.containsKey(nombre))
                .toList();

        return Map.of(
                "totalCinesEnCarteleraArgentina", todos.size(),
                "conPrecioDetectado", precios.size(),
                "sinPrecioDetectado", sinPrecio.size(),
                "porcentajeCobertura", Math.round((precios.size() * 10000.0) / todos.size()) / 100.0,
                "listaSinPrecio", sinPrecio
        );
    }

    // GET /api/test/total-precios-combinados — NO matchea nada todavía.
    // Suma lo que traen las dos fuentes de precio por separado, con
    // cachePrecios escopado por cadena (cadena → sucursal → formato →
    // precio) en vez del mapa plano viejo que permitía que dos
    // sucursales con el mismo nombre corto en cadenas distintas se
    // pisaran entre sí sin aviso.
    @GetMapping("/api/test/total-precios-combinados")
    public Map<String, Object> probarTotalPreciosCombinados() {
        Map<String, Map<String, Map<String, Double>>> porCadena = carteleraPreciosService.obtenerCachePreciosPorCadena();
        Map<String, Map<String, Double>> porCarteleraArgentina = carteleraArgentinaPreciosService.obtenerPreciosPropiosDescubiertos();

        int totalSucursalesPorCadena = porCadena.values().stream().mapToInt(Map::size).sum();

        List<String> clavesPorCadena = new ArrayList<>();
        for (var cadena : porCadena.entrySet()) {
            for (String sucursal : cadena.getValue().keySet()) {
                clavesPorCadena.add(cadena.getKey() + " / " + sucursal);
            }
        }

        return Map.of(
                "totalSucursalesAgendaDeCine", cinemaRepository.findByActiveTrue().size(),
                "cadenasConDatos", porCadena.keySet(),
                "preciosDetectadosPorScrapersDeCadena", totalSucursalesPorCadena,
                "clavesPreciosPorCadena", clavesPorCadena,
                "preciosDetectadosEnCarteleraArgentina", porCarteleraArgentina.size(),
                "clavesPreciosCarteleraArgentina", porCarteleraArgentina.keySet(),
                "sumaSimpleSinMatchear", totalSucursalesPorCadena + porCarteleraArgentina.size()
        );
    }

    // GET /api/test/precios-detallados-showcase — confirma que
    // cachePreciosDetallados (general/matinee/niños/promo) se llenó bien.
    @GetMapping("/api/test/precios-detallados-showcase")
    public Map<String, Object> probarPreciosDetallados() {
        return Map.of("detalle", carteleraPreciosService.obtenerPreciosDetallados());
    }

    // GET /api/test/precios-cache — vuelca cadena → sucursal → formato →
    // precio, para chequear valores contra las webs y no solo las claves.
    @GetMapping("/api/test/precios-cache")
    public Map<String, Map<String, Map<String, Double>>> verPreciosCache() {
        return carteleraPreciosService.obtenerCachePreciosPorCadena();
    }

    // GET /api/test/formatos-por-cadena — formatos que llegan desde
    // agendadecine en las funciones, por cadena, con su cantidad. La
    // primera llamada puede tardar: si la caché de funciones está fría,
    // baja las funciones de todas las películas en cartelera.
    @GetMapping("/api/test/formatos-por-cadena")
    public Map<String, Map<String, Long>> verFormatosPorCadena() {
        return agendaDeCineScraperService.obtenerFormatosPorCadena();
    }

    // GET /api/test/recargar-precios-manuales — vuelve a leer la tabla
    // cinema_manual_prices sin reiniciar ni re-scrapear las cadenas. Sirve
    // para ver al instante un INSERT o UPDATE hecho en la base.
    @GetMapping("/api/test/recargar-precios-manuales")
    public Map<String, Object> recargarPreciosManuales() {
        return Map.of("cinesConPrecioManual", carteleraPreciosService.recargarPreciosManuales());
    }

    // GET /api/test/precio-del-dia?cine=Cinépolis Recoleta&formato=2D&fecha=2026-10-06&hora=20:00
    // Devuelve el precio que corresponde a esa función (regular o reducido), el
    // precio regular de ese día, si se aplicó una tarifa reducida, si es un 2x1
    // (dosPorUno), el precio online si es menor (precioOnline) y si el precio es
    // el de la sala base de una sucursal con salas premium (precioDesde).
    // "hora" es opcional (por defecto 20:00).
    @GetMapping("/api/test/precio-del-dia")
    public Map<String, Object> verPrecioDelDia(@RequestParam String cine,
                                               @RequestParam String formato,
                                               @RequestParam String fecha,
                                               @RequestParam(defaultValue = "20:00") String hora) {
        var precio = carteleraPreciosService.obtenerPrecioDelDia(cine, formato, LocalDate.parse(fecha), LocalTime.parse(hora));
        if (precio == null) return Map.of("sinPrecio", true);
        Map<String, Object> respuesta = new java.util.LinkedHashMap<>();
        respuesta.put("precio", precio.precio());
        respuesta.put("precioRegular", precio.precioRegular());
        respuesta.put("tarifaReducida", precio.tarifaReducida());
        respuesta.put("dosPorUno", precio.dosPorUno());
        respuesta.put("precioOnline", precio.precioOnline());
        respuesta.put("precioDesde", precio.precioDesde());
        return respuesta;
    }

    // GET /api/test/precios-programados — aumentos ya anunciados con su fecha de
    // vigencia (hoy, los bloques futuros de Cinemark): cadena → sucursal →
    // formato → {fecha → precio}.
    @GetMapping("/api/test/precios-programados")
    public Object verPreciosProgramados() {
        return carteleraPreciosService.obtenerPreciosProgramados();
    }

    // GET /api/test/actualizar-precios — vuelve a correr la actualización de
    // precios de todas las cadenas sin reiniciar el servidor. Sirve para
    // reintentar una cadena que falló (ej. Cinépolis con 403). Tarda
    // alrededor de un minuto; una cadena que ya tenía datos los conserva si
    // vuelve a fallar.
    @GetMapping("/api/test/actualizar-precios")
    public Map<String, Object> actualizarPrecios() {
        carteleraPreciosService.actualizarPrecios();
        Map<String, Map<String, Map<String, Double>>> porCadena = carteleraPreciosService.obtenerCachePreciosPorCadena();
        return Map.of(
                "cadenasConDatos", porCadena.keySet(),
                "totalSucursales", porCadena.values().stream().mapToInt(Map::size).sum()
        );
    }
}