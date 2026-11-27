package com.example.demo.domain.mision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * El UNIVERSO de sagas de Cinemarketer: las que se ofrecen en el asistente del feed (Por característica › Saga) y, desde el paso siguiente,
 * las únicas que usan las misiones. Vive en UN solo archivo (src/main/resources/sagas.json): para agregar o corregir una saga se edita ese
 * archivo y nada más. El front lo pide a GET /api/sagas.
 *
 * Una película es de la saga si está en la colección de TMDB de la saga (si tiene) o en su lista de películas. Eso es exactamente lo que muestra
 * el asistente. Las sagas armadas a mano no tienen colección (coleccion = null) y su lista es completa; su id es negativo.
 *
 * Si el archivo está mal (JSON roto, id repetido, saga sin películas), el servidor NO arranca: es preferible a funcionar con sagas equivocadas.
 */
@Component
public class SagasCatalogo {

    /** coleccion = id de la colección de TMDB (null si la saga está armada a mano) · peliculas = las de la lista (todas si es a mano; las extra si tiene colección). */
    public record Saga(long id, String nombre, Long coleccion, List<Long> peliculas) {}

    public static final String ARCHIVO = "sagas.json";

    private final int version;
    private final List<Saga> sagas;

    public SagasCatalogo() {
        this(leerArchivo());
    }

    private SagasCatalogo(String json) {
        try {
            JsonNode raiz = new ObjectMapper().readTree(json);
            this.version = raiz.path("version").asInt(1);
            this.sagas = List.copyOf(parsear(raiz.path("sagas")));
        } catch (IOException e) {
            throw new IllegalStateException("El archivo " + ARCHIVO + " no es un JSON válido: " + e.getMessage(), e);
        }
    }

    /** Para las pruebas: arma el catálogo desde un texto JSON. */
    public static SagasCatalogo desdeTexto(String json) { return new SagasCatalogo(json); }

    private static String leerArchivo() {
        try (InputStream in = SagasCatalogo.class.getClassLoader().getResourceAsStream(ARCHIVO)) {
            if (in == null) throw new IllegalStateException("Falta el archivo " + ARCHIVO + " en src/main/resources");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ARCHIVO + ": " + e.getMessage(), e);
        }
    }

    private static List<Saga> parsear(JsonNode nodos) {
        if (!nodos.isArray() || nodos.isEmpty()) throw new IllegalStateException(ARCHIVO + ": no tiene sagas");
        List<Saga> r = new ArrayList<>();
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode n : nodos) {
            if (!n.path("id").isIntegralNumber() || n.path("id").asLong() == 0) throw new IllegalStateException(ARCHIVO + ": una saga no tiene id válido: " + n);
            long id = n.path("id").asLong();
            String nombre = n.path("nombre").asText("").trim();
            if (nombre.isEmpty()) throw new IllegalStateException(ARCHIVO + ": la saga " + id + " no tiene nombre");
            if (!ids.add(id)) throw new IllegalStateException(ARCHIVO + ": el id " + id + " está repetido");
            Long coleccion = n.path("coleccion").isIntegralNumber() ? n.path("coleccion").asLong() : null;
            Set<Long> peliculas = new LinkedHashSet<>();
            for (JsonNode p : n.path("peliculas")) {
                if (!p.isIntegralNumber() || p.asLong() <= 0) throw new IllegalStateException(ARCHIVO + ": la saga «" + nombre + "» tiene una película inválida: " + p);
                peliculas.add(p.asLong());
            }
            if (coleccion == null && peliculas.isEmpty()) throw new IllegalStateException(ARCHIVO + ": la saga «" + nombre + "» no tiene colección ni películas");
            if (coleccion == null && id > 0) throw new IllegalStateException(ARCHIVO + ": la saga «" + nombre + "» no tiene colección, así que su id tiene que ser negativo");
            if (coleccion != null && id < 0) throw new IllegalStateException(ARCHIVO + ": la saga «" + nombre + "» tiene colección, así que su id no puede ser negativo");
            if (coleccion != null && coleccion != id) throw new IllegalStateException(ARCHIVO + ": la saga «" + nombre + "» tiene que usar como id el de su colección (" + coleccion + ")");
            r.add(new Saga(id, nombre, coleccion, List.copyOf(peliculas)));
        }
        return r;
    }

    public int version() { return version; }

    public List<Saga> todas() { return sagas; }

    public Optional<Saga> porId(long id) { return sagas.stream().filter(s -> s.id() == id).findFirst(); }
}
