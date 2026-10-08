package com.example.demo.application.services;

import com.example.demo.domain.cinema.Cinema;
import com.example.demo.domain.cinema.CinemaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sincroniza la tabla Cinema contra el directorio completo de
 * agendadecine.com/cines — 324 cines, las 24 jurisdicciones del país,
 * sin depender de qué película esté en cartelera en cada una (a
 * diferencia de AgendaDeCineScraperService, que solo ve los cines que
 * programan una película puntual).
 *
 * Usa agendaDeCineComplexId como clave de upsert: si el cine ya existe,
 * actualiza los campos sincronizados; si no, lo crea. Nunca toca
 * description, images ni active — esos son contenido propio de
 * Cinemarketer (ver comentario en Cinema.java).
 */
@Service
public class AgendaDeCineDirectorioService {

    private static final String URL_DIRECTORIO = "https://agendadecine.com/cines";
    private static final Pattern CHUNK_PATTERN =
            Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)</script>", Pattern.DOTALL);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CinemaRepository cinemaRepository;

    public AgendaDeCineDirectorioService(CinemaRepository cinemaRepository) {
        this.cinemaRepository = cinemaRepository;
    }

    public record CineDirectorio(
            String complexId,
            String nombre,
            String cadenaId,
            String cadenaNombre,
            String direccion,
            String ciudad,
            String provincia,
            Double lat,
            Double lng,
            String telefono,
            String sitioWeb,
            String slug
    ) {}

    public record ResultadoSync(int creados, int actualizados, int total) {}

    /** Trae el directorio completo, sin tocar la base — útil para revisar antes de sincronizar. */
    public List<CineDirectorio> obtenerDirectorio() throws Exception {
        Document doc = Jsoup.connect(URL_DIRECTORIO)
                .userAgent("Mozilla/5.0 (compatible; CinemarketerBot/1.0)")
                .timeout(15000)
                .get();

        String html = doc.outerHtml();
        List<CineDirectorio> resultado = new ArrayList<>();

        Matcher m = CHUNK_PATTERN.matcher(html);
        while (m.find()) {
            String chunkTexto = desescaparComoStringJson(m.group(1));
            resultado.addAll(extraerCinesDeChunk(chunkTexto));
        }

        return resultado;
    }

    /** Trae el directorio y hace upsert contra la tabla Cinema. */
    public ResultadoSync sincronizar() throws Exception {
        List<CineDirectorio> directorio = obtenerDirectorio();
        int creados = 0, actualizados = 0;
        LocalDateTime ahora = LocalDateTime.now();

        for (CineDirectorio c : directorio) {
            Cinema cinema = cinemaRepository.findByAgendaDeCineComplexId(c.complexId())
                    .orElse(null);

            boolean esNuevo = (cinema == null);
            if (esNuevo) {
                cinema = new Cinema();
                cinema.setAgendaDeCineComplexId(c.complexId());
                cinema.setActive(true); // default para cines nuevos; un admin puede desactivarlo después
            }

            // Campos sincronizados — se pisan siempre con lo último de la fuente.
            cinema.setName(c.nombre());
            cinema.setChain(c.cadenaNombre());
            cinema.setChainId(c.cadenaId());
            cinema.setProvince(c.provincia());
            cinema.setCity(c.ciudad());
            cinema.setAddress(c.direccion());
            cinema.setLat(c.lat());
            cinema.setLng(c.lng());
            cinema.setPhone(c.telefono());
            cinema.setWebsite(c.sitioWeb());
            cinema.setAgendaDeCineSlug(c.slug());
            cinema.setLastSyncedAt(ahora);

            // description, images y active NUNCA se tocan acá si el cine ya existía.

            cinemaRepository.save(cinema);
            if (esNuevo) creados++; else actualizados++;
        }

        return new ResultadoSync(creados, actualizados, directorio.size());
    }

    private String desescaparComoStringJson(String chunkEscapado) throws Exception {
        JsonNode nodo = objectMapper.readTree("\"" + chunkEscapado + "\"");
        return nodo.asText();
    }

    private List<CineDirectorio> extraerCinesDeChunk(String texto) {
        List<CineDirectorio> resultado = new ArrayList<>();
        String marcador = "\"chainName\":";
        int desde = 0;

        // Cada cine del directorio es un objeto JSON plano con "id","name",
        // "chainId","chainName","address","cityId","cityName","provinceId",
        // "provinceName","latitude","longitude","phone","website","barrio",
        // "slug","rooms" — a diferencia de la página de película, acá no
        // vienen envueltos en markup de React alrededor de cada uno, así que
        // alcanza con ubicar el '{' de apertura más cercano hacia atrás
        // desde "id" y el '}' de cierre balanceado.
        while (true) {
            int posChain = texto.indexOf(marcador, desde);
            if (posChain == -1) break;

            int inicio = buscarInicioObjeto(texto, posChain);
            int fin = buscarCierreDesde(texto, inicio);

            if (inicio == -1 || fin == -1) {
                desde = posChain + marcador.length();
                continue;
            }

            String bloqueJson = texto.substring(inicio, fin + 1);
            try {
                JsonNode nodo = objectMapper.readTree(bloqueJson);
                if (nodo.has("id") && nodo.has("name") && nodo.has("provinceName")) {
                    resultado.add(new CineDirectorio(
                            textoOrNull(nodo, "id"),
                            textoOrNull(nodo, "name"),
                            textoOrNull(nodo, "chainId"),
                            textoOrNull(nodo, "chainName"),
                            textoOrNull(nodo, "address"),
                            textoOrNull(nodo, "cityName"),
                            textoOrNull(nodo, "provinceName"),
                            dobleOrNull(nodo, "latitude"),
                            dobleOrNull(nodo, "longitude"),
                            textoOrNull(nodo, "phone"),
                            textoOrNull(nodo, "website"),
                            textoOrNull(nodo, "slug")
                    ));
                }
            } catch (Exception e) {
                // Bloque no es un objeto de cine válido por sí solo — se descarta
                // y se sigue, igual que en AgendaDeCineScraperService.
            }

            desde = fin + 1;
        }

        return resultado;
    }

    private int buscarInicioObjeto(String texto, int posReferencia) {
        int profundidad = 0;
        for (int i = posReferencia; i >= 0; i--) {
            char c = texto.charAt(i);
            if (c == '}') profundidad++;
            else if (c == '{') {
                if (profundidad == 0) return i;
                profundidad--;
            }
        }
        return -1;
    }

    private int buscarCierreDesde(String texto, int inicio) {
        if (inicio == -1) return -1;
        int profundidad = 0;
        boolean dentroDeString = false;
        for (int i = inicio; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"' && (i == 0 || texto.charAt(i - 1) != '\\')) {
                dentroDeString = !dentroDeString;
                continue;
            }
            if (dentroDeString) continue;
            if (c == '{') profundidad++;
            else if (c == '}') {
                profundidad--;
                if (profundidad == 0) return i;
            }
        }
        return -1;
    }

    private String textoOrNull(JsonNode nodo, String campo) {
        JsonNode v = nodo.get(campo);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private Double dobleOrNull(JsonNode nodo, String campo) {
        JsonNode v = nodo.get(campo);
        return (v == null || v.isNull()) ? null : v.asDouble();
    }
}