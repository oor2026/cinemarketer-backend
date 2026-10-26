package com.example.demo.application.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Punto de referencia de la zona que una persona cargó en su perfil (provincia + localidad o barrio),
 * y utilidades de geografía que comparten los servicios de cartelera.
 *
 * El punto es el CENTRO de esa localidad o barrio (centroide de Georef/INDEC), no el domicilio: se usa
 * para medir "a cuánto queda cada cine de tu zona". No se manda ni se guarda ninguna ubicación de la
 * persona: se resuelve acá, a partir del texto de su perfil.
 *
 * Los puntos viven en src/main/resources/localidades-referencia.csv (provincia;localidad;lat;lng;fuente).
 * Si el archivo falta, el servicio arranca igual y simplemente no hay distancias.
 */
@Service
public class UbicacionReferenciaService {

    private static final Logger log = LoggerFactory.getLogger(UbicacionReferenciaService.class);
    private static final String RECURSO = "/localidades-referencia.csv";

    /**
     * Qué se considera "cerca tuyo": todo lo que está a este radio o menos, en línea recta, del punto de la zona
     * que la persona cargó en su perfil. Es el único lugar donde se define: para cambiarlo, se cambia acá.
     */
    public static final double RADIO_CERCA_KM = 10.0;

    /** Centro de una localidad. "localidad" es el nombre tal cual lo eligió la persona en su perfil. */
    public record Punto(double lat, double lng, String localidad) {}

    private final Map<String, Punto> puntos = new HashMap<>();

    public UbicacionReferenciaService() {
        cargar();
    }

    private void cargar() {
        try (InputStream in = UbicacionReferenciaService.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                log.warn("[ubicacion] No se encontró {}: no habrá distancias hasta que esté.", RECURSO);
                return;
            }
            BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String linea;
            boolean encabezado = true;
            while ((linea = lector.readLine()) != null) {
                if (encabezado) { encabezado = false; continue; }
                if (linea.isBlank()) continue;
                String[] c = linea.split(";");
                if (c.length < 4) continue;
                try {
                    puntos.put(claveProvincia(c[0]) + "|" + clave(c[1]),
                            new Punto(Double.parseDouble(c[2].trim()), Double.parseDouble(c[3].trim()), c[1].trim()));
                } catch (NumberFormatException e) {
                    log.warn("[ubicacion] Línea ignorada (coordenadas inválidas): {}", linea);
                }
            }
            log.info("[ubicacion] {} localidades con punto de referencia.", puntos.size());
        } catch (IOException e) {
            log.warn("[ubicacion] No se pudo leer {}: {}", RECURSO, e.getMessage());
        }
    }

    /**
     * Punto de la localidad que eligió la persona. La provincia puede venir como la guarda el perfil
     * ("Entre Ríos") o como la manda el front ("entre-rios", "caba"). Vacío si falta alguno de los dos
     * datos o si esa localidad no tiene punto.
     */
    public Optional<Punto> resolver(String provincia, String localidad) {
        if (provincia == null || localidad == null || localidad.isBlank()) return Optional.empty();
        return Optional.ofNullable(puntos.get(claveProvincia(provincia) + "|" + clave(localidad)));
    }

    // ------------------------------------------------------------------
    // Utilidades compartidas (estáticas: no dependen del estado del servicio)
    // ------------------------------------------------------------------

    /** Texto sin tildes, sin mayúsculas y sin nada que no sea letra o número: "Entre Ríos" y "entre-rios" dan lo mismo. */
    public static String clave(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Igual que clave(), pero unifica los nombres que se usan para una misma zona: el front manda "caba",
     * el directorio de cines dice "Capital Federal" y el perfil dice "Ciudad Autónoma de Buenos Aires";
     * y el directorio separa "Gran Buenos Aires" de "Buenos Aires", que para la persona es la misma provincia.
     */
    public static String claveProvincia(String texto) {
        String k = clave(texto);
        return switch (k) {
            case "caba", "capitalfederal", "ciudadautonomadebuenosaires", "ciudaddebuenosaires" -> "ciudadautonomadebuenosaires";
            case "granbuenosaires" -> "buenosaires";
            default -> k;
        };
    }

    /** Una coordenada sirve solo si es un par de números dentro de Argentina; una corrupta (coma decimal corrida, 0/0) se descarta. */
    public static boolean coordenadaValida(Double lat, Double lng) {
        return lat != null && lng != null && Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -56 && lat <= -21 && lng >= -74 && lng <= -53;
    }

    /** Distancia en línea recta entre dos puntos, en km (fórmula de Haversine). */
    public static double distanciaKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
