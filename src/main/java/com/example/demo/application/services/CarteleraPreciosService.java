package com.example.demo.application.services;

import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Precios de referencia por cine y formato — sacados de las páginas
 * oficiales de precios de cada cadena (no de la fuente de cartelera, que
 * no expone precio). Cache en memoria, sin persistencia en base todavía
 * (mismo criterio que el resto de Cartelera), actualizado una vez por
 * semana por CarteleraPreciosScheduler.
 *
 * IMPORTANTE — nivel de confianza de cada parser:
 * - Multiplex, Tadicor, Cinépolis: ALTA — se vieron tablas HTML reales,
 *   se parsean como tablas de verdad (Jsoup), no a ciegas.
 * - Showcase: ALTA — parseado contra el texto exacto que pasó el usuario.
 * - Cinemark, Atlas, Cinemacenter, Play Cinema, Santa Rosa: MEJOR
 *   ESFUERZO — construidos con lo visto en fetches anteriores, sin HTML
 *   crudo en mano. Si alguno devuelve vacío al probarlo en real, hay que
 *   revisar contra el HTML real y ajustar el regex/selector.
 * - Cinépolis: bloquea bots en la home, pero la ruta /index.php/precios
 *   sí respondió — si deja de funcionar, revisar si empezó a bloquear
 *   también esa ruta.
 */
@Service
public class CarteleraPreciosService {

    private static final Logger log = LoggerFactory.getLogger(CarteleraPreciosService.class);
    private static final String UA = "Mozilla/5.0 (compatible; CinemarketerBot/1.0)";

    private final CarteleraLiveScraperService carteleraLiveScraperService;

    public CarteleraPreciosService(CarteleraLiveScraperService carteleraLiveScraperService) {
        this.carteleraLiveScraperService = carteleraLiveScraperService;
    }

    // claveCine (nombre corto, tal cual aparece en la fuente de precios)
    // → precio por formato. El match contra nuestros propios nombres de
    // cine se resuelve en obtenerPrecioReferencia(), por "contiene".
    private Map<String, Map<String, Double>> cachePrecios = new HashMap<>();

    // Caso especial Dinosaurio: precio fijo cargado a mano (ver
    // actualizarPrecioManualDinosaurio), con fecha de referencia — no se
    // scrapea (el precio real está en un PDF semanal de Google Drive).
    private Double precioDinosaurio = null;
    private LocalDate fechaActualizacionDinosaurio = null;

    private final ReentrantLock lock = new ReentrantLock();

    public Double obtenerPrecioReferencia(String cineNombre, String formato) {
        String formatoNorm = formato == null ? "" : formato.trim().toUpperCase();
        String cineNorm = normalizar(cineNombre);

        // Caso especial: Dinosaurio no está en el cache scrapeado.
        if (cineNorm.contains("dinosaurio") && precioDinosaurio != null) {
            return precioDinosaurio;
        }

        String mejorMatch = null;
        for (String clave : cachePrecios.keySet()) {
            if (cineNorm.contains(normalizar(clave))) {
                if (mejorMatch == null || clave.length() > mejorMatch.length()) mejorMatch = clave;
            }
        }
        if (mejorMatch != null) {
            Map<String, Double> porFormato = cachePrecios.get(mejorMatch);
            for (var entry : porFormato.entrySet()) {
                if (formatoNorm.startsWith(entry.getKey())) return entry.getValue();
            }
        }

        // Fallback: precio propio sacado de carteleraargentina.com.ar,
        // cuando la ficha del cine lo traía — match exacto por nombre
        // completo (no por "contiene"), ya que esta cache la arma
        // CarteleraLiveScraperService con el nombre tal cual.
        Map<String, Double> porFormatoPropio = carteleraLiveScraperService.obtenerPreciosPropiosDescubiertos().get(cineNombre);
        if (porFormatoPropio != null) {
            for (var entry : porFormatoPropio.entrySet()) {
                if (formatoNorm.startsWith(entry.getKey())) return entry.getValue();
            }
        }

        return null;
    }

    /** Para mostrar la leyenda "Actualizado al dd/mm/aaaa" en Dinosaurio. */
    public String obtenerFechaActualizacionDinosaurio() {
        return fechaActualizacionDinosaurio == null ? null
                : fechaActualizacionDinosaurio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /** Carga manual desde el panel de admin (a construir más adelante). */
    public void actualizarPrecioManualDinosaurio(double precio) {
        this.precioDinosaurio = precio;
        this.fechaActualizacionDinosaurio = LocalDate.now();
    }

    /**
     * Calienta la cache al levantar el server — sin esto, quedaría vacía
     * hasta el primer lunes 00hs. Corre en un thread aparte para no
     * bloquear el arranque de la app mientras scrapea 9 sitios externos.
     */
    @PostConstruct
    public void calentarCacheAlArrancar() {
        new Thread(() -> {
            log.info("🎬 Calentando cache inicial de precios de cartelera...");
            actualizarPrecios();

            // Calienta también la cache de cines — es la única forma de
            // que cachePreciosPropios (el fallback de precios sacados de
            // carteleraargentina.com.ar) se llene, ya que solo se arma
            // como efecto secundario de descubrirCines(). Sin esto, esa
            // cache queda vacía hasta que algún usuario pida por
            // casualidad /cines-cercanos u /organizar-salida primero.
            log.info("🎬 Calentando cache de cines (necesaria para precios propios de carteleraargentina.com.ar)...");
            carteleraLiveScraperService.obtenerCinesConUbicacion();
        }, "cartelera-precios-warmup").start();
    }

    public void actualizarPrecios() {
        lock.lock();
        try {
            Map<String, Map<String, Double>> nuevo = new HashMap<>();

            ejecutarSinRomperElResto(nuevo, "Cinemark", this::scrapearCinemark);
            ejecutarSinRomperElResto(nuevo, "Atlas", this::scrapearAtlas);
            ejecutarSinRomperElResto(nuevo, "Cinemacenter", this::scrapearCinemacenter);
            ejecutarSinRomperElResto(nuevo, "Multiplex", this::scrapearMultiplex);
            ejecutarSinRomperElResto(nuevo, "Play Cinema", this::scrapearPlayCinema);
            ejecutarSinRomperElResto(nuevo, "Tadicor", this::scrapearTadicor);
            ejecutarSinRomperElResto(nuevo, "Santa Rosa", this::scrapearSantaRosa);
            ejecutarSinRomperElResto(nuevo, "Showcase", this::scrapearShowcase);
            ejecutarSinRomperElResto(nuevo, "Cinépolis", this::scrapearCinepolis);
            ejecutarSinRomperElResto(nuevo, "Cinema Devoto", this::scrapearCinemaDevoto);
            ejecutarSinRomperElResto(nuevo, "Gran Rex", this::scrapearGranRex);
            ejecutarSinRomperElResto(nuevo, "Cines del Solar", this::scrapearCinesDelSolar);
            ejecutarSinRomperElResto(nuevo, "Cinema Adrogué", this::scrapearCinemaAdrogue);

            cachePrecios = nuevo;
            log.info("Precios de referencia actualizados — {} sucursales con datos", nuevo.size());
        } finally {
            lock.unlock();
        }
    }

    private interface Scraper { Map<String, Map<String, Double>> scrapear() throws Exception; }

    private void ejecutarSinRomperElResto(Map<String, Map<String, Double>> destino, String cadena, Scraper scraper) {
        try {
            destino.putAll(scraper.scrapear());
        } catch (Exception e) {
            log.warn("No se pudo actualizar precios de {}: {}", cadena, e.getMessage());
        }
    }

    // ===================================================================
    // Helpers comunes
    // ===================================================================

    private void agregarPrecio(Map<String, Map<String, Double>> destino, String sucursal, String formato, double precio) {
        destino.computeIfAbsent(sucursal, k -> new LinkedHashMap<>()).put(formato, precio);
    }

    private double parsearPrecio(String texto) {
        return Double.parseDouble(texto.replace(".", "").replace(",", "").trim());
    }

    private String normalizar(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    /**
     * Recorre el documento en orden (headings + tablas) y arma
     * sucursal→formato→precio a partir de tablas HTML reales — para
     * fuentes donde confirmamos que el precio viene en un <table>
     * de verdad (Multiplex, Tadicor, Cinépolis).
     */
    private Map<String, Map<String, Double>> scrapearPorTablas(Document doc) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();
        String sucursalActual = null;

        for (Element el : doc.select("h1, h2, h3, h4, table")) {
            if (el.tagName().equals("table")) {
                if (sucursalActual == null) continue;
                for (Element row : el.select("tr")) {
                    List<Element> cells = row.select("td, th");
                    if (cells.size() < 2) continue;
                    String etiqueta = cells.get(0).text();
                    String precioTxt = cells.get(cells.size() - 1).text();
                    Matcher mp = Pattern.compile("\\$\\s*([\\d.,]+)").matcher(precioTxt);
                    if (!mp.find()) continue;
                    double precio = parsearPrecio(mp.group(1));

                    String etiquetaUpper = etiqueta.toUpperCase();
                    String formato = etiquetaUpper.contains("3D") ? "3D"
                            : etiquetaUpper.contains("4D") ? "4D"
                            : "2D";
                    Map<String, Double> porFormato = resultado.computeIfAbsent(sucursalActual, k -> new LinkedHashMap<>());
                    porFormato.putIfAbsent(formato, precio);
                }
            } else {
                String texto = el.text().trim();
                if (!texto.isBlank()) sucursalActual = texto;
            }
        }
        return resultado;
    }

    // ===================================================================
    // Cinemark — MEJOR ESFUERZO, verificar contra HTML real
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinemark() throws Exception {
        Document doc = Jsoup.connect("https://www.cinemark.com.ar/precios").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        List<String> sucursales = List.of("Palermo", "Caballito", "Puerto Madero", "Rosario", "Mendoza",
                "Unicenter", "Dot", "Abasto", "Moron", "Quilmes", "Nuevocentro", "Patio Olmos",
                "Temperley", "Moreno", "San Justo", "Malvinas Argentinas", "Tortugas", "Neuquen",
                "Alto Avellaneda", "Parque Brown", "Santa Fe", "Soleil");

        for (String sucursal : sucursales) {
            int idx = texto.indexOf(sucursal);
            if (idx < 0) continue;
            String bloque = texto.substring(idx, Math.min(texto.length(), idx + 700));

            Matcher m2d = Pattern.compile("SALAS\\s*2D[\\s\\S]{0,250}?\\$\\s*([\\d.,]+)").matcher(bloque);
            if (m2d.find()) agregarPrecio(resultado, sucursal, "2D", parsearPrecio(m2d.group(1)));

            Matcher m3d = Pattern.compile("SALAS\\s*3D[\\s\\S]{0,250}?\\$\\s*([\\d.,]+)").matcher(bloque);
            if (m3d.find()) agregarPrecio(resultado, sucursal, "3D", parsearPrecio(m3d.group(1)));
        }
        return resultado;
    }

    // ===================================================================
    // Atlas — MEJOR ESFUERZO, verificar contra HTML real
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearAtlas() throws Exception {
        Document doc = Jsoup.connect("https://precios-atlas.pages.dev").userAgent(UA).timeout(20000).get();
        return scrapearPorTablas(doc);
    }

    // ===================================================================
    // Cinemacenter — MEJOR ESFUERZO, verificar contra HTML real
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinemacenter() throws Exception {
        Document doc = Jsoup.connect("https://www.cinemacenter.com.ar/precios#contenido").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // El sitio repite, para cada sucursal y formato, un marcador
        // inequívoco: "Entradas 2D: Cinemacenter {Sucursal}. ... ENTRADA
        // GENERAL : ... $ {precio}" — confirmado con texto real. Mucho
        // más confiable que intentar adivinar el nombre de la sucursal
        // mirando hacia atrás (eso agarraba texto del menú de navegación
        // por error, ej. "CONSIDERACIONES GENERALES").
        Matcher m = Pattern.compile(
                "Entradas\\s+(2D|3D):\\s*Cinemacenter\\s+([^.]+?)\\.\\s*ENTRADA GENERAL\\s*:[^$]{0,80}?\\$\\s*([\\d.,]+)"
        ).matcher(texto);
        while (m.find()) {
            String formato = m.group(1);
            String sucursal = m.group(2).trim();
            agregarPrecio(resultado, sucursal, formato, parsearPrecio(m.group(3)));
        }
        System.out.println("[precios-cinemacenter] Sucursales capturadas: " + resultado.keySet()); // TEMPORAL
        return resultado;
    }

    // ===================================================================
    // Multiplex — ALTA confianza (tabla HTML real)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearMultiplex() throws Exception {
        Document doc = Jsoup.connect("https://multiplex.com.ar/precios/").userAgent(UA).timeout(20000).get();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // El nombre de sucursal vive en un acordeón de Elementor
        // (div.e-n-accordion-item-title-text), no en un heading normal —
        // confirmado con HTML real. Se recorre el documento en orden,
        // igual que scrapearPorTablas(), pero con este selector puntual.
        String sucursalActual = null;
        for (var el : doc.select("div.e-n-accordion-item-title-text, table")) {
            if (el.tagName().equals("table")) {
                if (sucursalActual == null) continue;
                for (var fila : el.select("tr")) {
                    var celdas = fila.select("td, th");
                    if (celdas.size() < 2) continue;
                    String sala = celdas.get(0).text().trim();
                    if (sala.isBlank() || sala.equalsIgnoreCase("Sala")) continue; // fila de encabezado

                    // La "Tarifa General" es siempre el precio SIN
                    // descuento — por diseño, el más alto de la fila
                    // (las promociones y descuentos son todos ≤ que él).
                    // Más robusto que fijar un índice de columna, porque
                    // la cantidad de columnas de promo varía por sucursal.
                    Double maxPrecio = null;
                    for (int c = 1; c < celdas.size(); c++) {
                        Matcher mp = Pattern.compile("\\$\\s*([\\d.,]+)").matcher(celdas.get(c).text());
                        if (mp.find()) {
                            double v = parsearPrecio(mp.group(1));
                            if (maxPrecio == null || v > maxPrecio) maxPrecio = v;
                        }
                    }
                    if (maxPrecio == null) continue;

                    String formato = sala.toUpperCase().startsWith("3D") ? "3D" : "2D";
                    resultado.computeIfAbsent(sucursalActual, k -> new LinkedHashMap<>()).putIfAbsent(formato, maxPrecio);
                }
            } else {
                String texto = el.text().trim();
                if (texto.startsWith("Multiplex ")) sucursalActual = texto.substring("Multiplex ".length()).trim();
            }
        }
        return resultado;
    }

    // ===================================================================
    // Play Cinema — un solo complejo (San Juan), precio simple
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearPlayCinema() throws Exception {
        Document doc = Jsoup.connect("https://playcinema.net/#promociones").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        Matcher m = Pattern.compile("2D/3D:\\s*\\$\\s*([\\d.,]+)\\s*/\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m.find()) {
            agregarPrecio(resultado, "Play Cinema", "2D", parsearPrecio(m.group(1)));
            agregarPrecio(resultado, "Play Cinema", "3D", parsearPrecio(m.group(2)));
        }
        return resultado;
    }

    // ===================================================================
    // Tadicor — ALTA confianza (tabla HTML real)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearTadicor() throws Exception {
        Document doc = Jsoup.connect("https://www.cinestadicor.com.ar/DynamicPages?id_section=34").userAgent(UA).timeout(20000).get();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Tadicor usa formato de número AMERICANO (coma = miles, punto =
        // decimal — ej. "$12,000.00") a diferencia de todas las demás
        // fuentes, que usan formato latino ("$12.000"). Por eso no reusa
        // scrapearPorTablas()/parsearPrecio() genéricos — esos asumían
        // formato latino y convertían $12,000.00 en $1.200.000 por error
        // (confirmado con datos reales).
        for (var tabla : doc.select("table")) {
            for (var fila : tabla.select("tr")) {
                var celdas = fila.select("td, th");
                if (celdas.size() < 2) continue;
                String etiqueta = celdas.get(0).text();
                String precioTxt = celdas.get(celdas.size() - 1).text();
                Matcher mp = Pattern.compile("\\$\\s*([\\d,]+\\.\\d{2})").matcher(precioTxt);
                if (!mp.find()) continue;
                double precio = Double.parseDouble(mp.group(1).replace(",", ""));
                String formato = etiqueta.toUpperCase().contains("3D") ? "3D" : "2D";
                resultado.computeIfAbsent("Tadicor", k -> new LinkedHashMap<>()).putIfAbsent(formato, precio);
            }
        }
        return resultado;
    }

    // ===================================================================
    // Santa Rosa (Amadeus/Milenium) — MEJOR ESFUERZO
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearSantaRosa() throws Exception {
        Document doc = Jsoup.connect("https://www.cinesantarosa.com.ar/DynamicPages?id_section=34").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        Matcher m2d = Pattern.compile("GENERAL\\s*2D[\\s\\S]{0,40}?\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE).matcher(texto);
        if (m2d.find()) {
            agregarPrecio(resultado, "Amadeus", "2D", parsearPrecio(m2d.group(1)));
            agregarPrecio(resultado, "Milenium", "2D", parsearPrecio(m2d.group(1)));
        }
        return resultado;
    }

    // ===================================================================
    // Showcase — ALTA confianza (texto exacto confirmado por el usuario)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearShowcase() throws Exception {
        Document doc = Jsoup.connect("https://www.todoshowcase.com/precios").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // TEMPORAL — diagnóstico completo
        System.out.println("[precios] Longitud total del texto: " + texto.length());
        int nOcurrencias = 0;
        int pos = 0;
        while ((pos = texto.indexOf("Showcase Belgrano", pos)) >= 0) {
            nOcurrencias++;
            System.out.println("[precios] Ocurrencia #" + nOcurrencias + " en índice " + pos + ": >>>"
                    + texto.substring(pos, Math.min(texto.length(), pos + 300)) + "<<<");
            pos += 1;
        }
        System.out.println("[precios] Total ocurrencias de 'Showcase Belgrano': " + nOcurrencias);

        int idxSalas = texto.toLowerCase().indexOf("salas 2d");
        System.out.println("[precios] Primera aparición de 'salas 2d' (case-insensitive) en índice: " + idxSalas);
        if (idxSalas >= 0) {
            int desde = Math.max(0, idxSalas - 300);
            System.out.println("[precios] Contexto alrededor de 'salas 2d': >>>" + texto.substring(desde, Math.min(texto.length(), idxSalas + 300)) + "<<<");
        }
        // FIN TEMPORAL
        List<String> sucursales = List.of("Belgrano", "Norcenter", "Haedo", "Quilmes",
                "Córdoba Villa Cabrera", "Villa Allende", "Rosario");

        for (String sucursal : sucursales) {
            // La página tiene un menú de navegación que también menciona
            // "Showcase {sucursal}" ANTES del contenido real de precios
            // (confirmado con log real) — indexOf simple agarraba esa
            // mención del menú, no la sección de precios. Se busca, entre
            // TODAS las apariciones del nombre, la primera que tenga la
            // palabra "Salas" a menos de 200 caracteres después (que sí
            // es exclusiva del contenido real de precios).
            int idx = -1;
            int desde = 0;
            while (true) {
                int candidato = texto.indexOf("Showcase " + sucursal, desde);
                if (candidato < 0) break;
                String ventana = texto.substring(candidato, Math.min(texto.length(), candidato + 200));
                if (ventana.toLowerCase().contains("salas")) { idx = candidato; break; }
                desde = candidato + 1;
            }
            if (idx < 0) {
                System.out.println("[precios] Showcase " + sucursal + ": NO encontrado (ninguna aparición cerca de 'Salas')"); // TEMPORAL
                continue;
            }
            int finBloque = texto.indexOf("Showcase ", idx + 1);
            String bloque = texto.substring(idx, finBloque > 0 ? finBloque : Math.min(texto.length(), idx + 1500));
            if (sucursal.equals("Belgrano")) {
                System.out.println("[precios] BLOQUE BELGRANO CRUDO >>>" + bloque + "<<<"); // TEMPORAL
            }

            // [^\d]{0,20} en vez de \.*\s* — tolera cualquier símbolo entre
            // "domingos" y el "$" (elipsis real "…", dos puntos, espacios
            // no separables, etc.), no solo puntos sueltos y espacios
            // normales. Confirmado con datos reales: el "..." que se ve al
            // copiar el texto de esta página es una elipsis Unicode de un
            // solo carácter, no 3 puntos — \.* nunca la matcheaba.
            Matcher m2d = Pattern.compile("Salas\\s*2d\\s*General de jueves a domingos[^\\d]{0,20}\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE).matcher(bloque);
            if (m2d.find()) agregarPrecio(resultado, sucursal, "2D", parsearPrecio(m2d.group(1)));

            Matcher m3d = Pattern.compile("Salas\\s*3d\\s*General de jueves a domingos[^\\d]{0,20}\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE).matcher(bloque);
            if (m3d.find()) agregarPrecio(resultado, sucursal, "3D", parsearPrecio(m3d.group(1)));
        }

        Matcher mImax = Pattern.compile("IMAX THEATRE[\\s\\S]{0,100}?General[^\\d]{0,20}\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE).matcher(texto);
        if (mImax.find()) agregarPrecio(resultado, "Norcenter", "IMAX", parsearPrecio(mImax.group(1)));

        return resultado;
    }

    // ===================================================================
    // Cinépolis — ALTA confianza (tablas Markdown reales confirmadas)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinepolis() throws Exception {
        Document doc = Jsoup.connect("https://www.cinepolis.com.ar/index.php/precios").userAgent(UA).timeout(20000).get();
        Map<String, Map<String, Double>> porTabla = scrapearPorTablas(doc);

        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();
        for (var entry : porTabla.entrySet()) {
            String sucursal = entry.getKey().replaceFirst("(?i)^Cinépolis\\s*", "").trim();
            if (sucursal.isBlank()) continue;
            resultado.put(sucursal, entry.getValue());
        }
        return resultado;
    }

    // ===================================================================
    // Cinema Devoto — MEJOR ESFUERZO, verificar contra HTML real
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinemaDevoto() throws Exception {
        Document doc = Jsoup.connect("https://cinemadevoto.com.ar/precios/").userAgent(UA).timeout(20000).get();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Tabla real (confirmada con HTML): fila "GENERAL (SÁBADOS,
        // DOMINGOS Y JUEVES)" trae, en ese orden, el precio 2D con
        // impuestos primero, luego "Precio sin impuestos...", luego el
        // 3D con impuestos, luego su "sin impuestos" — se toma el primer
        // "$" de cada mitad (2D y 3D), ignorando los "sin impuestos".
        var tablas = doc.select("table");
        if (tablas.isEmpty()) return resultado;

        for (var fila : tablas.first().select("tr")) {
            String etiqueta = fila.text();
            if (!etiqueta.toUpperCase().contains("GENERAL")) continue;

            Matcher m = Pattern.compile("\\$\\s*([\\d.,]+)").matcher(etiqueta);
            List<Double> precios = new ArrayList<>();
            while (m.find()) precios.add(parsearPrecio(m.group(1)));

            // precios[0] = 2D con impuestos, precios[1] = 2D sin
            // impuestos, precios[2] = 3D con impuestos, precios[3] = 3D
            // sin impuestos — se usa el "con impuestos" real de cada uno.
            if (precios.size() >= 1) agregarPrecio(resultado, "Cinema Devoto", "2D", precios.get(0));
            if (precios.size() >= 3) agregarPrecio(resultado, "Cinema Devoto", "3D", precios.get(2));
        }

        return resultado;
    }

    // ===================================================================
    // Gran Rex — ALTA confianza (texto exacto confirmado)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearGranRex() throws Exception {
        Document doc = Jsoup.connect("http://cinesgranrex.com.ar/promociones").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // TEMPORAL — diagnóstico: este parser era "mejor esfuerzo" desde
        // el inicio, nunca confirmado contra HTML real.
        System.out.println("[precios-granrex] Longitud del texto: " + texto.length());
        int idx2d = texto.indexOf("2D");
        if (idx2d >= 0) {
            System.out.println("[precios-granrex] Contexto alrededor de '2D': >>>"
                    + texto.substring(Math.max(0, idx2d - 50), Math.min(texto.length(), idx2d + 250)) + "<<<");
        } else {
            System.out.println("[precios-granrex] '2D' NO aparece en el texto en absoluto");
        }
        System.out.println("[precios-granrex] Cantidad de <table>: " + doc.select("table").size());

        Matcher m2d = Pattern.compile("Precios\\s*2D[\\s\\S]{0,120}?Entrada general\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m2d.find()) agregarPrecio(resultado, "Gran Rex", "2D", parsearPrecio(m2d.group(1)));

        Matcher m3d = Pattern.compile("Precios\\s*3D[\\s\\S]{0,120}?Entrada general\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m3d.find()) agregarPrecio(resultado, "Gran Rex", "3D", parsearPrecio(m3d.group(1)));

        return resultado;
    }

    // ===================================================================
    // Cines del Solar — ALTA confianza (vía sitio del shopping, texto exacto)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinesDelSolar() throws Exception {
        Document doc = Jsoup.connect("https://solardelcerro.com/").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Se toma el precio de fin de semana (Jueves a Domingo) como
        // referencia — es el que más se acerca al concepto de "General"
        // que usamos en el resto de las cadenas.
        Matcher m = Pattern.compile("Jueves, Viernes, S[aá]bados, Domingos y feriados:[\\s\\S]{0,60}?2D\\s*\\$\\s*([\\d.,]+)[\\s\\S]{0,20}?3D\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m.find()) {
            agregarPrecio(resultado, "Cines del Solar", "2D", parsearPrecio(m.group(1)));
            agregarPrecio(resultado, "Cines del Solar", "3D", parsearPrecio(m.group(2)));
        }

        return resultado;
    }

    // ===================================================================
    // Cinema Adrogué — ALTA confianza (texto exacto confirmado)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinemaAdrogue() throws Exception {
        Document doc = Jsoup.connect("https://www.cinemaadrogue.com/DynamicPages?id_section=28").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Acá el formato viene del encabezado de sección ("2D · Salas
        // estándar" / "3D · Salas 3D"), no de cada fila de la tabla — por
        // eso se usa regex sobre el texto completo en vez de
        // scrapearPorTablas (que asume que el encabezado es una sucursal).
        Matcher m2d = Pattern.compile("2D\\s*[·•]\\s*Salas est[aá]ndar[\\s\\S]{0,100}?General\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m2d.find()) agregarPrecio(resultado, "Cinema Adrogué", "2D", parsearPrecio(m2d.group(1)));

        Matcher m3d = Pattern.compile("3D\\s*[·•]\\s*Salas 3D[\\s\\S]{0,100}?General\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m3d.find()) agregarPrecio(resultado, "Cinema Adrogué", "3D", parsearPrecio(m3d.group(1)));

        return resultado;
    }
}
