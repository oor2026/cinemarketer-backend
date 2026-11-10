package com.example.demo.infrastructure.external.tmdb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Pósters para el muro del login mobile (películas y series en tendencia de la semana, mezcladas).
 *
 * El login es público: se llama sin sesión y por cualquiera. Por eso este servicio guarda el resultado en memoria y NO deja
 * que cada visita pegue contra TMDB:
 *   - la lista se reutiliza VIGENCIA_MS (6 horas);
 *   - si TMDB falla, se sigue sirviendo la última lista buena (o una vacía) y no se reintenta antes de REINTENTO_MS;
 *   - si varias visitas llegan a la vez con la lista vencida, solo una va a TMDB.
 * Devuelve solo la ruta del póster (por ejemplo "/abc.jpg"); la imagen la baja el navegador directo de TMDB.
 */
@Service
public class LoginPostersService {

    private static final Logger LOG = Logger.getLogger(LoginPostersService.class.getName());
    private static final int MAXIMO = 32;                       // alcanza para el muro (24) con margen para repetir menos
    private static final int PAGINAS = 2;                       // 20 resultados por página
    private static final long VIGENCIA_MS = 6L * 60 * 60 * 1000;
    private static final long REINTENTO_MS = 60L * 1000;
    private static final Pattern RUTA_VALIDA = Pattern.compile("^/[A-Za-z0-9._-]+\\.(jpg|jpeg|png|webp)$");

    private final RestTemplate restTemplate;
    private final String apiToken;
    private final String baseUrl;

    private final Object candado = new Object();
    private volatile List<String> posters = List.of();
    private volatile long vigenteHasta = 0;
    private volatile long reintentarDespues = 0;

    public LoginPostersService(
            RestTemplate restTemplate,
            @Value("${tmdb.api.token}") String apiToken,
            @Value("${tmdb.api.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiToken = apiToken;
        this.baseUrl = baseUrl;
    }

    /** Rutas de pósters, en orden de popularidad. Vacía si todavía no se pudo traer ninguna. Nunca lanza excepción. */
    public List<String> obtener() {
        long ahora = System.currentTimeMillis();
        if (ahora < vigenteHasta) return posters;
        synchronized (candado) {
            ahora = System.currentTimeMillis();
            if (ahora < vigenteHasta) return posters;           // otra visita ya la renovó mientras esperaba
            if (ahora < reintentarDespues) return posters;      // falló hace poco: se sirve lo último que había
            try {
                List<String> nuevos = pedir();
                if (nuevos.isEmpty()) {
                    reintentarDespues = ahora + REINTENTO_MS;
                } else {
                    posters = nuevos;
                    vigenteHasta = ahora + VIGENCIA_MS;
                }
            } catch (Exception e) {
                reintentarDespues = ahora + REINTENTO_MS;
                LOG.log(Level.WARNING, "No se pudieron traer los pósters del login desde TMDB: " + e.getMessage());
            }
            return posters;
        }
    }

    private List<String> pedir() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        Set<String> rutas = new LinkedHashSet<>();
        for (int pagina = 1; pagina <= PAGINAS && rutas.size() < MAXIMO; pagina++) {
            String url = UriComponentsBuilder.fromUriString(baseUrl + "/trending/all/week")
                    .queryParam("language", "es-ES")
                    .queryParam("page", String.valueOf(pagina))
                    .build().toUriString();
            ResponseEntity<Object> respuesta = restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);
            extraer(respuesta.getBody(), rutas, MAXIMO);
        }
        return Collections.unmodifiableList(new ArrayList<>(rutas));
    }

    /**
     * Saca de la respuesta de TMDB las rutas de póster de películas y series (no personas), sin contenido para adultos, sin
     * repetidos y con una ruta de forma conocida, hasta llegar al tope. Tolera cualquier forma rara de la respuesta.
     */
    static void extraer(Object cuerpo, Set<String> destino, int tope) {
        if (!(cuerpo instanceof Map<?, ?> mapa) || !(mapa.get("results") instanceof List<?> items)) return;
        for (Object item : items) {
            if (destino.size() >= tope) return;
            if (!(item instanceof Map<?, ?> r)) continue;
            Object tipo = r.get("media_type");
            if (tipo != null && !"movie".equals(tipo) && !"tv".equals(tipo)) continue;
            if (Boolean.TRUE.equals(r.get("adult"))) continue;
            if (r.get("poster_path") instanceof String ruta && RUTA_VALIDA.matcher(ruta).matches()) destino.add(ruta);
        }
    }
}
