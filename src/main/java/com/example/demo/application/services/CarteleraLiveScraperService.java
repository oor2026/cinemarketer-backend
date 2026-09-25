package com.example.demo.application.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraping en vivo de carteleraargentina.com.ar, con cache simple en
 * memoria (30 min). A propósito, todavía NO persiste nada en base — eso
 * queda para cuando esté listo el scraper completo de cines del país
 * (entidad Cine, health check, scheduler). Esto es el paso intermedio
 * para tener datos reales ya mismo en el Buscador Asistido.
 */
@Service
public class CarteleraLiveScraperService {

    private static final String BASE = "https://www.carteleraargentina.com.ar";

    /**
     * Hoyts dejó de existir como marca en Argentina (rebranding a
     * Cinemark, febrero 2026) — pero la fuente todavía nombra a esos
     * cines como "Hoyts {Sucursal}". Se normaliza acá, en el origen,
     * para que el cambio se propague solo a todo lo que usa el nombre
     * del cine (display, logo, precio, matching) sin tener que tocar
     * cada pantalla por separado.
     */
    private String normalizarNombreCine(String nombre) {
        if (nombre != null && nombre.startsWith("Hoyts ")) {
            return "Cinemark " + nombre.substring("Hoyts ".length());
        }
        return nombre;
    }

    // Precios sacados oportunistamente de carteleraargentina.com.ar,
    // cuando la ficha del cine trae la sección — capa de respaldo para
    // cines sin scraper propio confirmado, o donde ese scraper falle.
    private Map<String, Map<String, Double>> cachePreciosPropios = new HashMap<>();

    public Map<String, Map<String, Double>> obtenerPreciosPropiosDescubiertos() {
        return cachePreciosPropios;
    }
    private static final Pattern SLUG_PELICULA = Pattern.compile("/peliculas/([a-z0-9-]+)/");
    private static final Pattern FECHA_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2})");

    // Confirmado a mano contra el menú real del sitio: son exactamente
    // estas 15 (CABA cae bajo "buenos-aires" en la URL de la fuente y se
    // separa después según la dirección/nombre del cine). La fuente NO
    // cubre Chaco, Chubut, Corrientes, Entre Ríos, Formosa, Río Negro,
    // Santa Cruz ni Tierra del Fuego — no tiene sentido pedirlas, siempre
    // van a devolver vacío. Nuestro propio catálogo del combo (24
    // provincias) sigue completo igual — esas 8 simplemente van a dar
    // "no encontramos cines" cuando se elijan, que es correcto.
    private static final List<String> PROVINCIAS = List.of(
            "buenos-aires", "catamarca", "cordoba", "jujuy", "la-pampa", "la-rioja",
            "mendoza", "misiones", "neuquen", "salta", "san-juan", "san-luis",
            "santa-fe", "santiago-del-estero", "tucuman"
    );

    // 2 segmentos después de /cines/ = un cine real; 1 segmento = índice
    // de provincia. Mismo criterio ya validado para descubrir películas
    // por /peliculas/.
    private static final Pattern PATRON_CINE = Pattern.compile("/cines/([a-z-]+)/([a-z0-9-]+)/?$");

    private List<String> cacheUrlsCines = null;
    private LocalDateTime cacheUrlsCinesExpira = null;

    /**
     * Descubre TODAS las URLs de cines del país, recorriendo las 15
     * provincias. Cache largo (24 hs) — a diferencia de películas/
     * funciones, la lista de cines de un país prácticamente no cambia de
     * un día para otro.
     */
    private List<String> descubrirUrlsCinesDelPais() {
        lock.lock();
        try {
            if (cacheUrlsCines != null && cacheUrlsCinesExpira != null && LocalDateTime.now().isBefore(cacheUrlsCinesExpira)) {
                return cacheUrlsCines;
            }

            Set<String> urls = new LinkedHashSet<>();
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<Future<Document>> futures = new ArrayList<>();
            for (String provincia : PROVINCIAS) {
                String urlProvincia = BASE + "/cines/" + provincia + "/";
                futures.add(executor.submit(() -> Jsoup.connect(urlProvincia)
                        .userAgent("Mozilla/5.0 (compatible; CinemarketerBot/1.0)")
                        .timeout(15000)
                        .get()));
            }
            int i = 0;
            for (Future<Document> future : futures) {
                String provinciaActual = PROVINCIAS.get(i++);
                try {
                    Document doc = future.get(20, TimeUnit.SECONDS);
                    int antes = urls.size();
                    for (Element link : doc.select("a[href*=/cines/]")) {
                        String href = link.attr("href").split("\\?")[0]; // cortar ?zona=... antes de matchear
                        Matcher m = PATRON_CINE.matcher(href);
                        if (m.find()) {
                            urls.add(href.startsWith("http") ? href : BASE + href);
                        }
                    }
                    System.out.println("[cartelera] " + provinciaActual + ": " + (urls.size() - antes) + " cines encontrados"); // TEMPORAL — diagnóstico
                } catch (Exception e) {
                    System.out.println("[cartelera] Falló la provincia '" + provinciaActual + "': " + e.getClass().getSimpleName() + " - " + e.getMessage()); // TEMPORAL — diagnóstico
                }
            }
            executor.shutdown();

            cacheUrlsCines = new ArrayList<>(urls);
            cacheUrlsCinesExpira = LocalDateTime.now().plusHours(24);
            return cacheUrlsCines;
        } finally {
            lock.unlock();
        }
    }

    private List<PeliculaEnCartelera> cachePeliculas = null;
    private LocalDateTime cacheExpira = null;
    private final ReentrantLock lock = new ReentrantLock();

    public List<PeliculaEnCartelera> obtenerPeliculasEnCartelera() {
        lock.lock();
        try {
            if (cachePeliculas != null && cacheExpira != null && LocalDateTime.now().isBefore(cacheExpira)) {
                return cachePeliculas;
            }
            cachePeliculas = descubrirPeliculas();
            cacheExpira = LocalDateTime.now().plusMinutes(30);
            return cachePeliculas;
        } finally {
            lock.unlock();
        }
    }

    private List<PeliculaEnCartelera> descubrirPeliculas() {
        // Una sola pasada: la ficha de cada cine ya trae título Y póster
        // de cada película (confirmado mirando el HTML real) — no hace
        // falta entrar a la ficha de cada película por separado. Esto solo
        // ya elimina, en promedio, tantos pedidos extra como películas
        // distintas haya (antes se pedía 2 veces cada una).
        //
        // Cada película aparece 2 veces en el link: una envolviendo el
        // <img> (esa es la del póster) y otra envolviendo el texto del
        // título (la del <h3>) — se combinan por slug.
        Map<String, String> titulos = new LinkedHashMap<>();
        Map<String, String> posters = new LinkedHashMap<>();

        // Hasta 5 cines en simultáneo — con ~100-150 cines en vez de 10,
        // subimos un poco el paralelo respecto al primer prototipo, pero
        // seguimos lejos de golpear el sitio sin control.
        List<String> urlsCines = descubrirUrlsCinesDelPais();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Document>> futures = new ArrayList<>();
        for (String urlCine : urlsCines) {
            futures.add(executor.submit(() -> Jsoup.connect(urlCine)
                    .userAgent("Mozilla/5.0 (compatible; CinemarketerBot/1.0)")
                    .timeout(15000)
                    .get()));
        }

        for (Future<Document> future : futures) {
            try {
                Document doc = future.get(20, java.util.concurrent.TimeUnit.SECONDS);
                for (Element link : doc.select("a[href*=/peliculas/]")) {
                    Matcher m = SLUG_PELICULA.matcher(link.attr("href"));
                    if (!m.find()) continue;
                    String slug = m.group(1);

                    Element img = link.selectFirst("img");
                    if (img != null) {
                        // El sitio usa lazy-load (WP Rocket): el src real
                        // suele venir en data-lazy-src, y src trae un
                        // placeholder vacío mientras tanto.
                        String src = img.attr("src");
                        if (src.isBlank() || src.startsWith("data:")) {
                            src = img.attr("data-lazy-src");
                        }
                        if (!src.isBlank()) posters.putIfAbsent(slug, src);
                    } else {
                        String texto = link.text().trim();
                        if (!texto.isBlank()) titulos.putIfAbsent(slug, texto);
                    }
                }
            } catch (Exception ignored) {
                // Un cine caído (o lento) no debe tirar abajo el descubrimiento entero.
            }
        }
        executor.shutdown();

        // Segunda pasada, mucho más chica — una request por PELÍCULA
        // distinta en cartelera (no por cine), para sacar el género. El
        // género solo vive en la ficha propia de la película, no en la
        // página del cine, así que no hay forma de evitar esta pasada
        // extra sin perder el dato.
        Map<String, List<String>> generos = new LinkedHashMap<>();
        List<String> slugsOrdenados = new ArrayList<>(titulos.keySet());
        ExecutorService executorGenero = Executors.newFixedThreadPool(5);
        List<Future<Document>> futuresGenero = new ArrayList<>();
        for (String slug : slugsOrdenados) {
            futuresGenero.add(executorGenero.submit(() -> Jsoup.connect(BASE + "/peliculas/" + slug + "/")
                    .userAgent("Mozilla/5.0 (compatible; CinemarketerBot/1.0)")
                    .timeout(15000)
                    .get()));
        }
        for (int i = 0; i < slugsOrdenados.size(); i++) {
            try {
                Document docPelicula = futuresGenero.get(i).get(20, java.util.concurrent.TimeUnit.SECONDS);
                Matcher mGenero = GENERO_PELICULA.matcher(docPelicula.text());
                if (mGenero.find()) {
                    List<String> lista = java.util.Arrays.stream(mGenero.group(1).split(","))
                            .map(String::trim)
                            .filter(g -> !g.isBlank())
                            .toList();
                    generos.put(slugsOrdenados.get(i), lista);
                }
            } catch (Exception ignored) {
                // Sin género para esta película puntual no debe tirar abajo el resto.
            }
        }
        executorGenero.shutdown();

        List<PeliculaEnCartelera> resultado = new ArrayList<>();
        for (String slug : titulos.keySet()) {
            resultado.add(new PeliculaEnCartelera(slug, titulos.get(slug), posters.get(slug), generos.getOrDefault(slug, List.of())));
        }
        return resultado;
    }

    /**
     * Reusa el mismo parser ya validado (probado antes contra HTML real)
     * para traer TODAS las funciones de una película: cine, provincia,
     * día, horario, formato, idioma.
     */
    public List<FuncionScrapeada> obtenerFunciones(String slug) throws Exception {
        // Sin mapa conocido: lo arma llamando a obtenerPeliculasEnCartelera()
        // — SEGURO acá, porque esta sobrecarga la usa el endpoint directo
        // (/peliculas/{slug}/funciones), que nunca corre con el lock de
        // clase ya tomado.
        Map<String, String> tituloAPoster = obtenerPeliculasEnCartelera().stream()
                .collect(java.util.stream.Collectors.toMap(
                        PeliculaEnCartelera::titulo, p -> p.poster() == null ? "" : p.poster(),
                        (a, b) -> a));
        return obtenerFunciones(slug, tituloAPoster);
    }

    // Sobrecarga interna — usada por obtenerTodasLasFuncionesCacheadas(),
    // que llama a esto en paralelo para cada película MIENTRAS ya tiene
    // el lock de clase tomado. Recibir el mapa ya armado evita que cada
    // llamada intente volver a tomar ese mismo lock (lo cual generaba un
    // deadlock real: el hilo que sostiene el lock nunca lo suelta porque
    // espera a estos futures, y los futures nunca terminan porque están
    // bloqueados esperando ese mismo lock — confirmado con el log real,
    // el "null" que aparecía era un TimeoutException por este motivo).
    private List<FuncionScrapeada> obtenerFunciones(String slug, Map<String, String> tituloAPosterConocido) throws Exception {
        Document doc = Jsoup.connect(BASE + "/peliculas/" + slug + "/")
                .userAgent("Mozilla/5.0 (compatible; CinemarketerBot/1.0)")
                .timeout(15000)
                .get();

        Element h1 = doc.selectFirst("h1.title");
        String tituloPelicula = h1 != null ? h1.text() : slug;
        List<FuncionScrapeada> funciones = new ArrayList<>();
        int anioActual = LocalDate.now().getYear();

        String posterPelicula = tituloAPosterConocido.get(tituloPelicula);
        if (posterPelicula != null && posterPelicula.isBlank()) posterPelicula = null;

        for (Element provinciaBox : doc.select(".provincia-box")) {
            String provincia = provinciaBox.attr("data-prov");

            for (Element cineItem : provinciaBox.select(".cine-card-item")) {
                Element linkCine = cineItem.selectFirst("a");
                if (linkCine == null) continue;
                String cineNombre = normalizarNombreCine(linkCine.text());

                for (Element diaBlock : cineItem.children()) {
                    if (!diaBlock.attr("style").contains("margin-top:10px")) continue;

                    Element spanFecha = diaBlock.selectFirst("span[style*=text-transform:capitalize]");
                    if (spanFecha == null) continue;

                    Matcher m = FECHA_PATTERN.matcher(spanFecha.text().trim());
                    if (!m.find()) continue;
                    LocalDate fecha = LocalDate.of(anioActual,
                            Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
                    boolean esHoy = diaBlock.selectFirst(".titilante") != null;

                    for (Element fila : diaBlock.select("div[style*=display:flex]")) {
                        Element badge = fila.selectFirst(".badge-formato");
                        var spans = fila.select("span");
                        if (badge == null || spans.size() < 2) continue;

                        String horariosCrudo = spans.last().text().trim();
                        if (horariosCrudo.isBlank()) continue;

                        String[] partes = separarFormatoIdioma(badge.text().trim());

                        for (String horaTexto : horariosCrudo.split(",\\s*")) {
                            funciones.add(new FuncionScrapeada(
                                    tituloPelicula, provincia, cineNombre, fecha, esHoy,
                                    LocalTime.parse(horaTexto.trim(), DateTimeFormatter.ofPattern("HH:mm")),
                                    partes[0], partes[1], posterPelicula
                            ));
                        }
                    }
                }
            }
        }
        return funciones;
    }

    private String[] separarFormatoIdioma(String texto) {
        for (String idioma : List.of("CASTELLANO", "SUBTITULADA", "SUBTITULADO")) {
            if (texto.endsWith(idioma)) {
                return new String[]{texto.substring(0, texto.length() - idioma.length()).trim(), idioma};
            }
        }
        return new String[]{texto, ""};
    }

    // ===================================================================
    // Cines cercanos — Paso 1 ("¿Dónde?") de "¿Qué hay para ver?"
    // Reusa los mismos 10 cines semilla. La ubicación de un cine casi no
    // cambia, así que el cache dura mucho más que el de películas (6 hs).
    // ===================================================================
    private List<CineConUbicacion> cacheCines = null;
    private LocalDateTime cacheCinesExpira = null;
    private static final Pattern LAT_LNG = Pattern.compile("query=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");
    private static final Pattern PROVINCIA_DE_URL = Pattern.compile("/cines/([a-z-]+)/");
    // Busca "Género {lista separada por comas}" en la Ficha Técnica de la
    // película — confirmado que la fuente lo trae ahí, ej: "Género Comedia,
    // Aventura, Fantasía, Familiar". Se corta en el próximo campo conocido
    // de la ficha (Origen) para no arrastrar texto de más.
    private static final Pattern GENERO_PELICULA = Pattern.compile("G[eé]nero\\s*([^\\n]+?)\\s*Origen");
    // Sección "Precios" que ALGUNOS cines tienen en su propia ficha de
    // carteleraargentina.com.ar (contenido editorial, no sistemático —
    // no todos los cines la tienen). Cuando existe, viene como HTML
    // estático real (confirmado con Showcase Belgrano), no JS — a
    // diferencia de casi todos los sitios propios de cada cadena.
    // [\s\u00A0] en vez de \s — Java \s no matchea el espacio irrompible
    // (non-breaking space, U+00A0), que aparece seguido en contenido
    // editado con editores WYSIWYG como el de WordPress. Ya nos pasó
    // algo parecido con Showcase (elipsis Unicode) — misma familia de bug.
    private static final Pattern PRECIO_CARTELERA_AR = Pattern.compile(
            "Entradas[\\s\\u00A0]+(2D|3D)\\b([\\s\\u00A0]+Superseat)?[\\s\\S]{0,50}?General[\\s\\u00A0]*\\(jueves a domingos\\)[^\\d]{0,20}\\$[\\s\\u00A0]*([\\d.,]+)");
    // Busca "Dirección: {texto}" ANCLADO al link "📍 Cómo llegar" que
    // siempre lo sigue de cerca — evita cortar mal en abreviaturas como
    // "Av." y evita matchear "Dirección" en el sentido de "director de
    // una película" de alguna reseña en esa misma página.
    //
    // A propósito, ya NO se intenta derivar acá una única "localidad
    // limpia" — se probó y la fuente es demasiado inconsistente (algunas
    // direcciones terminan en la calle, otras en el barrio, otras en la
    // provincia repetida). Se guarda el texto crudo completo, y el
    // matching contra una localidad real (de nuestro propio catálogo) se
    // hace más adelante, buscando si el texto la menciona — más robusto
    // que intentar clasificar de antemano.
    private static final Pattern DIRECCION = Pattern.compile("Direcci[oó]n:\\s*([\\s\\S]{3,250}?)\\s*📍\\s*Cómo llegar");

    public List<CineConUbicacion> obtenerCinesConUbicacion() {
        lock.lock();
        try {
            if (cacheCines != null && cacheCinesExpira != null && LocalDateTime.now().isBefore(cacheCinesExpira)) {
                return cacheCines;
            }
            cacheCines = descubrirCines();
            cacheCinesExpira = LocalDateTime.now().plusHours(6);
            return cacheCines;
        } finally {
            lock.unlock();
        }
    }

    private List<CineConUbicacion> descubrirCines() {
        List<String> urlsCines = descubrirUrlsCinesDelPais();
        List<CineConUbicacion> resultado = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Document>> futures = new ArrayList<>();
        for (String urlCine : urlsCines) {
            futures.add(executor.submit(() -> Jsoup.connect(urlCine)
                    .userAgent("Mozilla/5.0 (compatible; CinemarketerBot/1.0)")
                    .timeout(15000)
                    .get()));
        }

        int i = 0;
        for (Future<Document> future : futures) {
            String urlCine = urlsCines.get(i++);
            try {
                Document doc = future.get(20, TimeUnit.SECONDS);
                Element h1 = doc.selectFirst("h1");
                String nombre = normalizarNombreCine(h1 != null ? h1.text() : urlCine);
                // Las coordenadas vienen embebidas en el link "Cómo llegar"
                // a Google Maps — mismo hallazgo que ya habíamos validado a mano.
                Element linkMaps = doc.selectFirst("a[href*=google.com/maps]");
                if (linkMaps == null) {
                    System.out.println("[cartelera] Cine descartado (sin link 'Cómo llegar'): " + urlCine); // TEMPORAL
                    continue;
                }
                Matcher m = LAT_LNG.matcher(linkMaps.attr("href"));
                if (!m.find()) {
                    System.out.println("[cartelera] Cine descartado (link Maps sin lat/lng parseable): " + urlCine); // TEMPORAL
                    continue;
                }

                double lat = Double.parseDouble(m.group(1));
                double lng = Double.parseDouble(m.group(2));

                Matcher mProv = PROVINCIA_DE_URL.matcher(urlCine);
                String provincia = mProv.find() ? mProv.group(1) : null;

                String direccionCompleta = "";
                Matcher mDir = DIRECCION.matcher(doc.text());
                if (mDir.find()) direccionCompleta = mDir.group(1);

                // Precio oportunista — si esta ficha puntual trae la
                // sección "Precios" (no todas la tienen), se guarda acá,
                // sin necesidad de otro pedido HTTP.
                if (nombre.contains("Belgrano")) {
                    int idxPrecios = doc.text().indexOf("Precios");
                    if (idxPrecios >= 0) {
                        System.out.println("[precios-propios] BLOQUE PRECIOS BELGRANO >>>"
                                + doc.text().substring(idxPrecios, Math.min(doc.text().length(), idxPrecios + 400)) + "<<<"); // TEMPORAL
                    } else {
                        System.out.println("[precios-propios] '" + nombre + "': la palabra 'Precios' NO aparece en doc.text()"); // TEMPORAL
                    }
                }
                Matcher mPrecio = PRECIO_CARTELERA_AR.matcher(doc.text());
                while (mPrecio.find()) {
                    String formatoBase = mPrecio.group(1); // "2D" o "3D"
                    boolean superseat = mPrecio.group(2) != null;
                    String formatoClave = formatoBase + (superseat ? " Superseat" : "");
                    double precio;
                    try {
                        precio = Double.parseDouble(mPrecio.group(3).replace(".", "").replace(",", ""));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    cachePreciosPropios.computeIfAbsent(nombre, k -> new HashMap<>()).put(formatoClave, precio);
                }

                // CABA es una jurisdicción autónoma propia, no una localidad
                // de la Provincia de Buenos Aires — aunque la fuente los
                // agrupa bajo la misma URL (/cines/buenos-aires/). Se
                // separan según lo que diga la dirección real, o el
                // nombre del cine si menciona un barrio conocido.
                if ("buenos-aires".equals(provincia)) {
                    String dirNorm = normalizar(direccionCompleta);
                    boolean esCaba = dirNorm.contains("ciudad autonoma de buenos aires")
                            || dirNorm.contains("capital federal")
                            || derivarBarrioCaba(nombre) != null;
                    if (esCaba) provincia = "caba";
                }

                resultado.add(new CineConUbicacion(nombre, urlCine, lat, lng, provincia, direccionCompleta));
            } catch (Exception e) {
                System.out.println("[cartelera] Falló resolver cine '" + urlCine + "': " + e.getClass().getSimpleName() + " - " + e.getMessage()); // TEMPORAL
            }
        }
        executor.shutdown();
        return resultado;
    }

    /**
     * Ordena los cines conocidos por distancia real a un punto dado
     * (fórmula de Haversine). No filtra por radio — devuelve todos,
     * ordenados; el frontend decide cuántos mostrar.
     */
    public List<CineConDistancia> obtenerCinesCercanos(double lat, double lng) {
        return obtenerCinesConUbicacion().stream()
                .map(c -> new CineConDistancia(c.nombre(), c.url(), c.lat(), c.lng(),
                        distanciaKm(lat, lng, c.lat(), c.lng())))
                .sorted(Comparator.comparingDouble(CineConDistancia::distanciaKm))
                .toList();
    }

    /**
     * Fallback SIN coordenadas: filtra por el nombre del cine (ej.
     * "Palermo" encuentra "Cinemark Palermo"). No es distancia real — es
     * un proxy simple hasta que scrapeemos la dirección/localidad
     * estructurada de cada cine. Se usa cuando el usuario no dio permiso
     * de geolocalización.
     */
    public List<CineConUbicacion> obtenerCinesPorTexto(String texto) {
        List<CineConUbicacion> todos = obtenerCinesConUbicacion();
        if (texto == null || texto.isBlank()) return todos;
        String query = normalizar(texto);
        return todos.stream()
                .filter(c -> normalizar(c.nombre()).contains(query))
                .toList();
    }

    private String normalizar(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private double distanciaKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371; // radio de la Tierra en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(r * c * 10) / 10.0; // 1 decimal
    }

    public record CineConUbicacion(String nombre, String url, double lat, double lng, String provincia, String direccionCompleta) {}

    public record CineConDistancia(String nombre, String url, double lat, double lng, double distanciaKm) {}

    // ===================================================================
    // Cadenas de cine — "Por cadena de cine"
    // Deriva la cadena de la primera palabra del nombre del cine (ej.
    // "Cinemark Palermo" → "Cinemark"). Simple a propósito, sin mapear
    // sinónimos ni fusiones corporativas — se puede afinar después.
    // ===================================================================
    private Map<String, List<FuncionScrapeada>> cacheFuncionesPorSlug = null;
    private LocalDateTime cacheFuncionesExpira = null;

    // Barrios de CABA — se busca cuál de estos aparece DENTRO del nombre
    // del cine (la dirección scrapeada de CABA no trae el barrio, solo
    // dice "Ciudad Autónoma de Buenos Aires"; el barrio real está en
    // nombres como "Cinemark Palermo" o "Cine Atlas Caballito").
    // [nombre oficial, alias corto — el que suele usar el nombre del cine].
    // El match se hace contra AMBAS formas: muchos cines dicen "Devoto",
    // no "Villa Devoto" — con solo el nombre oficial se perdían.
    private static final List<String[]> BARRIOS_CABA = List.of(
            new String[]{"Villa Urquiza", "Urquiza"}, new String[]{"Villa Devoto", "Devoto"},
            new String[]{"Villa Crespo", "Crespo"}, new String[]{"Villa del Parque", "Villa del Parque"},
            new String[]{"Villa Lugano", "Lugano"}, new String[]{"Villa Luro", "Luro"},
            new String[]{"Villa Pueyrredón", "Pueyrredón"}, new String[]{"Villa Ortúzar", "Ortúzar"},
            new String[]{"Parque Chacabuco", "Parque Chacabuco"}, new String[]{"Parque Avellaneda", "Parque Avellaneda"},
            new String[]{"Parque Patricios", "Parque Patricios"}, new String[]{"Puerto Madero", "Puerto Madero"},
            new String[]{"Nueva Pompeya", "Pompeya"}, new String[]{"San Cristóbal", "San Cristóbal"},
            new String[]{"San Nicolás", "San Nicolás"}, new String[]{"San Telmo", "San Telmo"},
            new String[]{"La Paternal", "Paternal"}, new String[]{"La Boca", "Boca"},
            new String[]{"Palermo", "Palermo"}, new String[]{"Belgrano", "Belgrano"},
            new String[]{"Caballito", "Caballito"}, new String[]{"Liniers", "Liniers"},
            new String[]{"Flores", "Flores"}, new String[]{"Floresta", "Floresta"},
            new String[]{"Recoleta", "Recoleta"}, new String[]{"Almagro", "Almagro"},
            new String[]{"Balvanera", "Balvanera"}, new String[]{"Barracas", "Barracas"},
            new String[]{"Boedo", "Boedo"}, new String[]{"Chacarita", "Chacarita"},
            new String[]{"Coghlan", "Coghlan"}, new String[]{"Colegiales", "Colegiales"},
            new String[]{"Constitución", "Constitución"}, new String[]{"Mataderos", "Mataderos"},
            new String[]{"Monserrat", "Monserrat"}, new String[]{"Núñez", "Núñez"},
            new String[]{"Retiro", "Retiro"}, new String[]{"Saavedra", "Saavedra"},
            new String[]{"Versalles", "Versalles"}, new String[]{"Abasto", "Abasto"}
    );

    private String derivarBarrioCaba(String nombreCine) {
        String normalizado = normalizar(nombreCine);
        for (String[] barrio : BARRIOS_CABA) {
            if (normalizado.contains(normalizar(barrio[1]))) return barrio[0];
        }
        return null; // no se pudo determinar — queda sin localidad
    }

    // Lista de cadenas conocidas — se busca cuál de estas aparece DENTRO
    // del nombre del cine (no "primera palabra", que rompía con "Cine
    // Atlas Caballito" → derivaba "Cine" en vez de "Atlas").
    //
    // "Hoyts" mapea a "Cinemark" a propósito: en febrero de 2026 Cinemark
    // unificó la marca en Argentina y todos los ex-Hoyts pasaron a
    // llamarse Cinemark — pero la fuente (carteleraargentina.com.ar)
    // todavía no actualizó el nombre en su propio sitio. Los agrupamos
    // nosotros para que el usuario no vea dos cadenas separadas que en
    // la realidad son una sola.
    //
    // "IMAX" no es una cadena — es una tecnología de pantalla. La sala
    // que vimos con ese nombre (IMAX Norcenter) pertenece en realidad a
    // Showcase, por eso no está listada acá aparte.
    //
    // Ojo: "Cine", "Cinema", "Cines" y "Complejo" NO son cadenas — son
    // palabras genéricas que usan muchos cines INDEPENDIENTES sin
    // relación entre sí (ej. "Cine Gaumont" y "Cine Teatro Helios" no
    // tienen nada que ver). A propósito no están en esta lista — caen en
    // el fallback de abajo, que los deja cada uno como su propia entidad.
    private static final Map<String, String> CADENAS_CONOCIDAS = new LinkedHashMap<>() {{
        put("Cinemark", "Cinemark");
        put("Hoyts", "Cinemark");
        put("Cinépolis", "Cinépolis");
        put("Atlas", "Atlas");
        put("Multiplex", "Multiplex");
        put("Cinemacenter", "Cinemacenter");
        put("Showcase", "Showcase");
        put("Play Cinema", "Play Cinema");
        put("Dinosaurio", "Dinosaurio");
        put("Las Tipas", "Las Tipas");
        put("Tadicor", "Tadicor");
        put("Cine Amadeus", "Santa Rosa");
        put("Cine Milenium", "Santa Rosa");
    }};

    private String derivarCadena(String nombreCine) {
        String normalizado = normalizar(nombreCine);
        for (var entry : CADENAS_CONOCIDAS.entrySet()) {
            if (normalizado.contains(normalizar(entry.getKey()))) return entry.getValue();
        }
        // Fallback: cine independiente, sin cadena real conocida — se
        // deja con su propio nombre completo (NO la primera palabra),
        // para no agruparlo por error con otro independiente que
        // comparta una palabra genérica como "Cine" o "Complejo".
        return nombreCine;
    }

    public List<String> obtenerCadenasDisponibles() {
        return obtenerCinesConUbicacion().stream()
                .map(c -> derivarCadena(c.nombre()))
                .distinct()
                .sorted()
                .toList();
    }

    public List<FuncionScrapeada> obtenerFuncionesPorCadena(String cadena) {
        return obtenerTodasLasFuncionesCacheadas().values().stream()
                .flatMap(List::stream)
                .filter(f -> derivarCadena(f.cineNombre()).equals(cadena))
                .toList();
    }

    /**
     * Scrapea las funciones de TODAS las películas descubiertas, una sola
     * vez (cacheado 30 min, igual que el listado de películas) — evita
     * repetir el scraping completo cada vez que alguien elige una cadena
     * distinta.
     */
    private Map<String, List<FuncionScrapeada>> obtenerTodasLasFuncionesCacheadas() {
        lock.lock();
        try {
            if (cacheFuncionesPorSlug != null && cacheFuncionesExpira != null && LocalDateTime.now().isBefore(cacheFuncionesExpira)) {
                return cacheFuncionesPorSlug;
            }

            List<PeliculaEnCartelera> peliculas = obtenerPeliculasEnCartelera();

            // Armado UNA sola vez acá, no dentro de cada future — ver
            // comentario en obtenerFunciones(slug, mapa) sobre por qué
            // esto es obligatorio, no solo una optimización.
            Map<String, String> tituloAPoster = peliculas.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            PeliculaEnCartelera::titulo, p -> p.poster() == null ? "" : p.poster(),
                            (a, b) -> a));

            ExecutorService executor = Executors.newFixedThreadPool(3);
            Map<String, Future<List<FuncionScrapeada>>> futures = new LinkedHashMap<>();
            for (PeliculaEnCartelera p : peliculas) {
                futures.put(p.slug(), executor.submit(() -> obtenerFunciones(p.slug(), tituloAPoster)));
            }

            Map<String, List<FuncionScrapeada>> resultado = new LinkedHashMap<>();
            for (var entry : futures.entrySet()) {
                try {
                    resultado.put(entry.getKey(), entry.getValue().get(20, TimeUnit.SECONDS));
                } catch (Exception e) {
                    System.out.println("[cartelera] Falló el scraping de funciones para '" + entry.getKey() + "': " + e.getMessage()); // TEMPORAL — diagnóstico
                }
            }
            executor.shutdown();

            cacheFuncionesPorSlug = resultado;
            cacheFuncionesExpira = LocalDateTime.now().plusMinutes(30);
            return resultado;
        } finally {
            lock.unlock();
        }
    }

    public List<CineConUbicacion> obtenerCinesPorCadena(String cadena) {
        return obtenerCinesConUbicacion().stream()
                .filter(c -> derivarCadena(c.nombre()).equals(cadena))
                .toList();
    }

    public List<FuncionScrapeada> obtenerFuncionesPorCine(String nombreCine) {
        return obtenerTodasLasFuncionesCacheadas().values().stream()
                .flatMap(List::stream)
                .filter(f -> f.cineNombre().equals(nombreCine))
                .toList();
    }

    /**
     * El cruce real de "Organizar una salida": cines que caen en esa
     * provincia/localidad, funciones de esos cines en esa fecha exacta,
     * y (si se pidió) dentro de la franja horaria elegida.
     */
    public List<FuncionScrapeada> organizarSalida(String provincia, String localidad, java.time.LocalDate fecha, String horario) {
        String localidadNorm = (localidad == null || localidad.isBlank()) ? null : normalizar(localidad);

        java.util.Set<String> cinesMatch = obtenerCinesConUbicacion().stream()
                .filter(c -> provincia.equals(c.provincia()))
                .filter(c -> localidadNorm == null
                        || normalizar(c.direccionCompleta()).contains(localidadNorm)
                        || normalizar(c.nombre()).contains(localidadNorm))
                .map(CineConUbicacion::nombre)
                .collect(java.util.stream.Collectors.toSet());

        return obtenerTodasLasFuncionesCacheadas().values().stream()
                .flatMap(List::stream)
                .filter(f -> cinesMatch.contains(f.cineNombre()))
                .filter(f -> f.dia().equals(fecha))
                .filter(f -> coincideFranjaHoraria(f.horario(), horario))
                .sorted(Comparator.comparing(FuncionScrapeada::horario))
                .toList();
    }

    private boolean coincideFranjaHoraria(java.time.LocalTime hora, String franja) {
        if (franja == null || franja.isBlank()) return true;
        int h = hora.getHour();
        return switch (franja) {
            case "manana" -> h < 12;
            case "tarde" -> h >= 12 && h < 19;
            case "noche" -> h >= 19;
            default -> true;
        };
    }

    public List<FuncionScrapeada> obtenerFuncionesPorPeliculaYGeografia(String slug, String provincia, String localidad) throws Exception {
        List<FuncionScrapeada> todas = obtenerFunciones(slug);
        String localidadNorm = (localidad == null || localidad.isBlank()) ? null : normalizar(localidad);

        Set<String> cinesMatch = obtenerCinesConUbicacion().stream()
                .filter(c -> provincia.equals(c.provincia()))
                .filter(c -> localidadNorm == null
                        || normalizar(c.direccionCompleta()).contains(localidadNorm)
                        || normalizar(c.nombre()).contains(localidadNorm))
                .map(CineConUbicacion::nombre)
                .collect(java.util.stream.Collectors.toSet());

        return todas.stream().filter(f -> cinesMatch.contains(f.cineNombre())).toList();
    }

    public record PeliculaEnCartelera(String slug, String titulo, String poster, List<String> genero) {}

    public record FuncionScrapeada(
            String peliculaTitulo, String provincia, String cineNombre,
            LocalDate dia, boolean esHoy, LocalTime horario, String formato, String idioma,
            String poster
    ) {}
}
