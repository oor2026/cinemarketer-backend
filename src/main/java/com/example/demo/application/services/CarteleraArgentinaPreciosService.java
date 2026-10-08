package com.example.demo.application.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Único trabajo que le queda a carteleraargentina.com.ar: precios
 * oportunistas que ALGUNOS cines traen, como contenido editorial, en su
 * propia ficha — agendadecine.com no expone precio, así que esta sigue
 * siendo la única fuente que tenemos para ese dato, aunque sea parcial.
 *
 * Esto es lo que quedó vivo del viejo CarteleraLiveScraperService
 * (borrado al migrar cartelera a agendadecine.com) — específicamente la
 * parte de descubrirCines(), que de paso parsea la sección "Precios" de
 * cada ficha si existe. Todo lo demás que tenía esa clase (películas,
 * funciones, cadenas, organizarSalida) ya lo hace AgendaDeCineScraperService
 * — no se restauró acá a propósito, para no dejar dos fuentes de
 * cartelera vivas al mismo tiempo.
 *
 * El matching final (qué precio le corresponde a qué cine de nuestro
 * propio directorio) lo sigue haciendo CarteleraPreciosService, por
 * "contiene" sobre el nombre — no todos los cines de agendadecine.com
 * van a tener precio acá, y eso es esperado: CarteleraPreciosService
 * devuelve null cuando no hay match, y el front informa "no cuenta con
 * precio" en ese caso.
 */
@Service
public class CarteleraArgentinaPreciosService {

    private static final String BASE = "https://www.carteleraargentina.com.ar";

    /**
     * Hoyts dejó de existir como marca en Argentina (rebranding a
     * Cinemark, febrero 2026) — pero la fuente todavía nombra a esos
     * cines como "Hoyts {Sucursal}". Se normaliza acá, en el origen.
     */
    private String normalizarNombreCine(String nombre) {
        if (nombre != null && nombre.startsWith("Hoyts ")) {
            return "Cinemark " + nombre.substring("Hoyts ".length());
        }
        return nombre;
    }

    // Precios sacados oportunistamente de carteleraargentina.com.ar,
    // cuando la ficha del cine trae la sección — única fuente de precio
    // que tenemos hoy, ya que agendadecine.com no lo expone.
    private Map<String, Map<String, Double>> cachePreciosPropios = new HashMap<>();

    public Map<String, Map<String, Double>> obtenerPreciosPropiosDescubiertos() {
        return cachePreciosPropios;
    }

    // Confirmado a mano contra el menú real del sitio: son exactamente
    // estas 15 (CABA cae bajo "buenos-aires" en la URL de la fuente y se
    // separa después según la dirección/nombre del cine). La fuente NO
    // cubre Chaco, Chubut, Corrientes, Entre Ríos, Formosa, Río Negro,
    // Santa Cruz ni Tierra del Fuego.
    private static final List<String> PROVINCIAS = List.of(
            "buenos-aires", "catamarca", "cordoba", "jujuy", "la-pampa", "la-rioja",
            "mendoza", "misiones", "neuquen", "salta", "san-juan", "san-luis",
            "santa-fe", "santiago-del-estero", "tucuman"
    );

    private static final Pattern PATRON_CINE = Pattern.compile("/cines/([a-z-]+)/([a-z0-9-]+)/?$");

    private List<String> cacheUrlsCines = null;
    private LocalDateTime cacheUrlsCinesExpira = null;
    private final ReentrantLock lock = new ReentrantLock();

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
                    for (Element link : doc.select("a[href*=/cines/]")) {
                        String href = link.attr("href").split("\\?")[0];
                        Matcher m = PATRON_CINE.matcher(href);
                        if (m.find()) {
                            urls.add(href.startsWith("http") ? href : BASE + href);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[precios-propios] Falló la provincia '" + provinciaActual + "': " + e.getClass().getSimpleName() + " - " + e.getMessage()); // TEMPORAL
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

    // Sección "Precios" que ALGUNOS cines tienen en su propia ficha de
    // carteleraargentina.com.ar (contenido editorial, no sistemático).
    private static final Pattern PRECIO_CARTELERA_AR = Pattern.compile(
            "Entradas[\\s\\u00A0]+(2D|3D)\\b([\\s\\u00A0]+Superseat)?[\\s\\S]{0,50}?General[\\s\\u00A0]*\\(jueves a domingos\\)[^\\d]{0,20}\\$[\\s\\u00A0]*([\\d.,]+)");

    private static final Pattern LAT_LNG = Pattern.compile("query=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");
    private static final Pattern PROVINCIA_DE_URL = Pattern.compile("/cines/([a-z-]+)/");
    private static final Pattern DIRECCION = Pattern.compile("Direcci[oó]n:\\s*([\\s\\S]{3,250}?)\\s*📍\\s*Cómo llegar");

    private List<CineConUbicacion> cacheCines = null;
    private LocalDateTime cacheCinesExpira = null;

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

                Element linkMaps = doc.selectFirst("a[href*=google.com/maps]");
                if (linkMaps == null) continue;
                Matcher m = LAT_LNG.matcher(linkMaps.attr("href"));
                if (!m.find()) continue;

                double lat = Double.parseDouble(m.group(1));
                double lng = Double.parseDouble(m.group(2));

                Matcher mProv = PROVINCIA_DE_URL.matcher(urlCine);
                String provincia = mProv.find() ? mProv.group(1) : null;

                String direccionCompleta = "";
                Matcher mDir = DIRECCION.matcher(doc.text());
                if (mDir.find()) direccionCompleta = mDir.group(1);

                // TEMPORAL — diagnóstico: ver qué trae HOY la ficha de
                // Belgrano alrededor de "Precios", para confirmar si el
                // regex sigue vigente contra el sitio real.
                if (nombre.contains("Belgrano")) {
                    int idxPrecios = doc.text().indexOf("Precios");
                    if (idxPrecios >= 0) {
                        System.out.println("[precios-propios] BLOQUE PRECIOS BELGRANO >>>"
                                + doc.text().substring(idxPrecios, Math.min(doc.text().length(), idxPrecios + 400)) + "<<<");
                    } else {
                        System.out.println("[precios-propios] '" + nombre + "': la palabra 'Precios' NO aparece en doc.text()");
                    }
                }

                // Precio oportunista — si esta ficha puntual trae la
                // sección "Precios" (no todas la tienen), se guarda acá.
                Matcher mPrecio = PRECIO_CARTELERA_AR.matcher(doc.text());
                while (mPrecio.find()) {
                    String formatoBase = mPrecio.group(1);
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

                if ("buenos-aires".equals(provincia)) {
                    String dirNorm = normalizar(direccionCompleta);
                    boolean esCaba = dirNorm.contains("ciudad autonoma de buenos aires")
                            || dirNorm.contains("capital federal")
                            || derivarBarrioCaba(nombre) != null;
                    if (esCaba) provincia = "caba";
                }

                resultado.add(new CineConUbicacion(nombre, urlCine, lat, lng, provincia, direccionCompleta));
            } catch (Exception e) {
                System.out.println("[precios-propios] Falló resolver cine '" + urlCine + "': " + e.getClass().getSimpleName() + " - " + e.getMessage()); // TEMPORAL
            }
        }
        executor.shutdown();
        return resultado;
    }

    private String normalizar(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    public record CineConUbicacion(String nombre, String url, double lat, double lng, String provincia, String direccionCompleta) {}

    // Barrios de CABA — ver nota original: la dirección scrapeada de CABA
    // no trae el barrio, solo "Ciudad Autónoma de Buenos Aires"; el
    // barrio real está en el nombre del cine (ej. "Cinemark Palermo").
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
        return null;
    }
}