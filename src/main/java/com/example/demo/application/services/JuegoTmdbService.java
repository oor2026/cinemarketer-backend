package com.example.demo.application.services;

import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.application.services.JuegoAdminService.PreguntaRequest;
import com.example.demo.domain.juego.JuegoPregunta;
import com.example.demo.domain.juego.JuegoPreguntaRepository;
import com.example.demo.infrastructure.external.tmdb.TmdbService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Plataforma de juegos — preguntas generadas desde TMDb, acotadas a los temas
 * que elige el admin con el buscador: películas, series, sagas y personas,
 * uno o varios a la vez.
 *
 * Las preguntas NO se guardan acá: el admin revisa las candidatas y elige
 * cuáles entran al banco (POST .../preguntas/lote).
 *
 * Tipos de pregunta:
 *   Películas (sueltas, de una saga o de una persona)
 *     ¿De qué película es este póster? · ¿De qué película es esta sinopsis? ·
 *     ¿En qué año se estrenó X? · ¿Quién interpreta a Y en X? · ¿Quién dirigió X?
 *   Series
 *     ¿De qué serie es este póster? · ¿En qué año se estrenó la serie X? ·
 *     ¿Cuántas temporadas tiene X? · ¿Quién interpreta a Y en X? · ¿Quién creó X?
 *   Personas
 *     ¿A quién interpretó P en X? (y sus películas suman pósters y años)
 *   Todos: ¿Quién es? (foto de un actor del reparto)
 *
 * Las opciones incorrectas salen del conjunto de temas elegidos (otras películas,
 * otros actores, otros directores), así la pregunta no se resuelve por descarte.
 * Cada pregunta lleva una referencia ("movie:299536:poster") para no generar dos
 * veces la misma ni repetir una que ya está en el banco.
 */
@Service
public class JuegoTmdbService {

    public static final String PELICULA = "PELICULA";
    public static final String SERIE = "SERIE";
    public static final String SAGA = "SAGA";
    public static final String PERSONA = "PERSONA";

    private static final String IMG_POSTER = "https://image.tmdb.org/t/p/w500";
    private static final String IMG_PERFIL = "https://image.tmdb.org/t/p/w342";
    private static final String IMG_MINI = "https://image.tmdb.org/t/p/w92";
    private static final int MAX_TEMAS = 10;
    private static final int MAX_PELIS_PERSONA = 15;   // de una persona: sus más populares
    private static final int REPARTO = 8;              // actores principales por título
    private static final int MAX_RESULTADOS = 8;

    private final TmdbService tmdbService;
    private final JuegoPreguntaRepository preguntaRepository;
    private Random random = new Random();

    public JuegoTmdbService(TmdbService tmdbService, JuegoPreguntaRepository preguntaRepository) {
        this.tmdbService = tmdbService;
        this.preguntaRepository = preguntaRepository;
    }

    // Solo para pruebas.
    void usarAzar(Random random) { this.random = random; }

    // ===================================================================
    // Pedidos y respuestas
    // ===================================================================
    public record Tema(String tipo, Long id) {}

    public record GenerarRequest(List<Tema> temas, Integer cantidad) {}

    public record Generadas(List<String> temas, List<PreguntaRequest> preguntas, int descartadasPorRepetidas) {}

    // Un resultado del buscador.
    public record Resultado(String tipo, long id, String titulo, String anio, String imagen, String detalle) {}

    // Internos
    record Peli(long id, String titulo, String anio, String poster, String sinopsis, boolean conReparto) {}

    record Serie(long id, String nombre, String anio, String poster, int temporadas, List<String> creadores) {}

    record Actor(long id, String nombre, String personaje, String foto) {}

    // ===================================================================
    // Buscador
    // ===================================================================
    public List<Resultado> buscar(String tipo, String texto) {
        String q = texto == null ? "" : texto.trim();
        if (q.length() < 2) return List.of();
        String ruta = switch (tipo == null ? "" : tipo) {
            case PELICULA -> "movie";
            case SERIE -> "tv";
            case SAGA -> "collection";
            case PERSONA -> "person";
            default -> throw new JuegoException(400, "Tipo de búsqueda inválido");
        };
        Map<?, ?> respuesta;
        try {
            respuesta = mapa(tmdbService.buscarParaJuegos(ruta, q));
        } catch (JuegoException e) {
            throw e;
        } catch (Exception e) {
            throw new JuegoException(502, "No pudimos buscar en TMDb. Probá de nuevo");
        }
        List<Resultado> out = new ArrayList<>();
        for (Object o : lista(respuesta.get("results"))) {
            Map<?, ?> r = mapa(o);
            Resultado res = switch (tipo) {
                case PELICULA -> new Resultado(PELICULA, entero(r.get("id")), texto(r, "title"), anio(texto(r, "release_date")),
                        img(IMG_MINI, texto(r, "poster_path")), null);
                case SERIE -> new Resultado(SERIE, entero(r.get("id")), texto(r, "name"), anio(texto(r, "first_air_date")),
                        img(IMG_MINI, texto(r, "poster_path")), null);
                case SAGA -> new Resultado(SAGA, entero(r.get("id")), texto(r, "name"), null,
                        img(IMG_MINI, texto(r, "poster_path")), "Saga");
                default -> new Resultado(PERSONA, entero(r.get("id")), texto(r, "name"), null,
                        img(IMG_MINI, texto(r, "profile_path")), conocidoPor(r));
            };
            if (res.titulo() != null) out.add(res);
            if (out.size() >= MAX_RESULTADOS) break;
        }
        return out;
    }

    // "Actuación · Iron Man, Oppenheimer"
    private String conocidoPor(Map<?, ?> r) {
        List<String> titulos = new ArrayList<>();
        for (Object o : lista(r.get("known_for"))) {
            Map<?, ?> k = mapa(o);
            String t = texto(k, "title") != null ? texto(k, "title") : texto(k, "name");
            if (t != null) titulos.add(t);
            if (titulos.size() == 2) break;
        }
        String oficio = "Acting".equals(texto(r, "known_for_department")) ? "Actuación"
                : "Directing".equals(texto(r, "known_for_department")) ? "Dirección" : null;
        String obras = String.join(", ", titulos);
        if (oficio == null) return obras.isEmpty() ? null : obras;
        return obras.isEmpty() ? oficio : oficio + " · " + obras;
    }

    // ===================================================================
    // Generación
    // ===================================================================
    public Generadas generar(Long campaniaId, GenerarRequest req) {
        List<Tema> temas = req == null || req.temas() == null ? List.of() : req.temas().stream()
                .filter(t -> t != null && t.id() != null && t.tipo() != null).distinct().toList();
        if (temas.isEmpty()) throw new JuegoException(400, "Elegí al menos una película, serie, saga o persona");
        if (temas.size() > MAX_TEMAS) throw new JuegoException(400, "Se pueden elegir hasta " + MAX_TEMAS + " temas por vez");
        int cantidad = req.cantidad() == null ? 20 : Math.max(1, Math.min(req.cantidad(), 60));

        Map<Long, Peli> pelis = new LinkedHashMap<>();
        List<Serie> series = new ArrayList<>();
        List<String> nombresTemas = new ArrayList<>();
        // Persona → sus películas con el personaje que hizo en cada una.
        Map<String, List<Map<?, ?>>> filmografias = new LinkedHashMap<>();

        try {
            for (Tema t : temas) {
                switch (t.tipo()) {
                    case SAGA -> {
                        Map<?, ?> col = mapa(tmdbService.getCollection(t.id()));
                        nombresTemas.add("Saga: " + texto(col, "name"));
                        for (Object o : lista(col.get("parts"))) {
                            Peli p = peli(mapa(o), true);
                            if (p.titulo() != null && p.anio() != null) pelis.putIfAbsent(p.id(), p); // sin fecha = sin estrenar
                        }
                    }
                    case PELICULA -> {
                        // getMovieCountries devuelve la ficha completa de la película como mapa.
                        Peli p = peli(mapa(tmdbService.getMovieCountries(t.id())), true);
                        nombresTemas.add("Película: " + p.titulo());
                        if (p.titulo() != null) pelis.put(p.id(), p); // la elegida explícitamente reemplaza a la de una persona
                    }
                    case SERIE -> {
                        Map<?, ?> s = mapa(tmdbService.getSerieParaJuegos(t.id()));
                        List<String> creadores = new ArrayList<>();
                        for (Object o : lista(s.get("created_by"))) {
                            String n = texto(mapa(o), "name");
                            if (n != null) creadores.add(n);
                        }
                        Serie serie = new Serie(entero(s.get("id")), texto(s, "name"), anio(texto(s, "first_air_date")),
                                texto(s, "poster_path"), (int) numero(s.get("number_of_seasons")), creadores);
                        nombresTemas.add("Serie: " + serie.nombre());
                        if (serie.nombre() != null) series.add(serie);
                    }
                    case PERSONA -> {
                        String nombre = texto(mapa(tmdbService.getPersonDetails(t.id())), "name");
                        nombresTemas.add("Persona: " + nombre);
                        List<Map<?, ?>> suyas = new ArrayList<>();
                        for (Object o : lista(mapa(tmdbService.getPersonMovieCredits(t.id())).get("cast"))) {
                            Map<?, ?> c = mapa(o);
                            if (texto(c, "title") != null && anio(texto(c, "release_date")) != null) suyas.add(c);
                        }
                        suyas.sort(Comparator.comparingDouble((Map<?, ?> c) -> numero(c.get("popularity"))).reversed());
                        if (suyas.size() > MAX_PELIS_PERSONA) suyas = suyas.subList(0, MAX_PELIS_PERSONA);
                        filmografias.put(nombre, suyas);
                        for (Map<?, ?> c : suyas) pelis.putIfAbsent(entero(c.get("id")), peli(c, false));
                    }
                    default -> throw new JuegoException(400, "Tipo de tema inválido: " + t.tipo());
                }
            }
        } catch (JuegoException e) {
            throw e;
        } catch (Exception e) {
            throw new JuegoException(502, "No pudimos traer los datos de TMDb. Probá de nuevo");
        }

        List<PreguntaRequest> candidatas = armar(pelis, series, filmografias);

        // Fuera las que ya están en el banco de esta campaña.
        Set<String> enBanco = new HashSet<>();
        for (JuegoPregunta p : preguntaRepository.findByCampaniaIdOrderByDiaAsignadoAscOrdenEnDiaAscIdAsc(campaniaId)) {
            if (p.getReferenciaTmdb() != null) enBanco.add(p.getReferenciaTmdb());
        }
        int antes = candidatas.size();
        candidatas = new ArrayList<>(candidatas.stream().filter(p -> !enBanco.contains(p.referenciaTmdb())).toList());
        int repetidas = antes - candidatas.size();

        Collections.shuffle(candidatas, random);
        if (candidatas.size() > cantidad) candidatas = candidatas.subList(0, cantidad);
        if (candidatas.isEmpty()) {
            throw new JuegoException(409, repetidas > 0
                    ? "Todas las preguntas de esos temas ya están en el banco"
                    : "Esos temas no tienen datos suficientes en TMDb para armar preguntas. Probá sumando otro");
        }
        return new Generadas(nombresTemas, candidatas, repetidas);
    }

    List<PreguntaRequest> armar(Map<Long, Peli> pelis, List<Serie> series, Map<String, List<Map<?, ?>>> filmografias) {
        List<PreguntaRequest> out = new ArrayList<>();
        Set<String> referencias = new HashSet<>();

        // Repartos y directores, para preguntas y para distractores.
        Map<Long, Actor> actores = new LinkedHashMap<>();
        Map<Peli, List<Actor>> repartoPeli = new LinkedHashMap<>();
        Map<Peli, String> directorPeli = new LinkedHashMap<>();
        for (Peli p : pelis.values()) {
            if (!p.conReparto()) continue;
            Map<?, ?> cred = mapa(tmdbService.getMovieCredits(p.id()));
            List<Actor> reparto = actoresDePelicula(cred);
            repartoPeli.put(p, reparto);
            reparto.forEach(a -> actores.putIfAbsent(a.id(), a));
            for (Object o : lista(cred.get("crew"))) {
                Map<?, ?> c = mapa(o);
                if ("Director".equals(texto(c, "job")) && texto(c, "name") != null) {
                    directorPeli.put(p, texto(c, "name"));
                    break;
                }
            }
        }
        Map<Serie, List<Actor>> repartoSerie = new LinkedHashMap<>();
        for (Serie s : series) {
            List<Actor> reparto = actoresDeSerie(mapa(tmdbService.getSerieRepartoParaJuegos(s.id())));
            repartoSerie.put(s, reparto);
            reparto.forEach(a -> actores.putIfAbsent(a.id(), a));
        }

        List<String> titulosPelis = pelis.values().stream().map(Peli::titulo).toList();
        List<String> titulosSeries = series.stream().map(Serie::nombre).toList();
        List<String> nombresActores = actores.values().stream().map(Actor::nombre).toList();
        List<String> directores = new ArrayList<>(new LinkedHashSet<>(directorPeli.values()));
        List<String> creadores = series.stream().flatMap(s -> s.creadores().stream()).distinct().toList();

        // ---- Películas
        for (Peli p : pelis.values()) {
            if (p.poster() != null) {
                agregar(out, referencias, "¿De qué película es este póster?", IMG_POSTER + p.poster(), p.titulo(), titulosPelis, "movie:" + p.id() + ":poster");
            }
            if (p.sinopsis() != null && !p.sinopsis().toLowerCase(Locale.ROOT).contains(p.titulo().toLowerCase(Locale.ROOT))) {
                agregar(out, referencias, "¿De qué película es esta sinopsis? \"" + recortar(p.sinopsis(), 260) + "\"", null,
                        p.titulo(), titulosPelis, "movie:" + p.id() + ":sinopsis");
            }
            agregarAnio(out, referencias, "¿En qué año se estrenó " + p.titulo() + "?", p.anio(), "movie:" + p.id() + ":anio");
            for (Actor a : repartoPeli.getOrDefault(p, List.of())) {
                if (a.personaje() != null) {
                    agregar(out, referencias, "¿Quién interpreta a " + a.personaje() + " en " + p.titulo() + "?", null,
                            a.nombre(), nombresActores, "movie:" + p.id() + ":personaje:" + a.id());
                }
            }
            String director = directorPeli.get(p);
            if (director != null) {
                // Distractores: otros directores elegidos; si no hay, actores del conjunto.
                List<String> pool = directores.size() > 1 ? directores : nombresActores;
                agregar(out, referencias, "¿Quién dirigió " + p.titulo() + "?", null, director, pool, "movie:" + p.id() + ":director");
            }
        }

        // ---- Series
        for (Serie s : series) {
            if (s.poster() != null) {
                agregar(out, referencias, "¿De qué serie es este póster?", IMG_POSTER + s.poster(), s.nombre(), titulosSeries, "tv:" + s.id() + ":poster");
            }
            agregarAnio(out, referencias, "¿En qué año se estrenó la serie " + s.nombre() + "?", s.anio(), "tv:" + s.id() + ":anio");
            if (s.temporadas() > 0) {
                List<String> cercanos = new ArrayList<>();
                for (int d : new int[]{1, -1, 2, -2, 3, 4}) {
                    int n = s.temporadas() + d;
                    if (n >= 1) cercanos.add(String.valueOf(n));
                }
                agregar(out, referencias, "¿Cuántas temporadas tiene " + s.nombre() + "?", null, String.valueOf(s.temporadas()),
                        cercanos, "tv:" + s.id() + ":temporadas");
            }
            for (Actor a : repartoSerie.getOrDefault(s, List.of())) {
                if (a.personaje() != null) {
                    agregar(out, referencias, "¿Quién interpreta a " + a.personaje() + " en " + s.nombre() + "?", null,
                            a.nombre(), nombresActores, "tv:" + s.id() + ":personaje:" + a.id());
                }
            }
            if (s.creadores().size() == 1) {
                List<String> pool = creadores.size() > 1 ? creadores : nombresActores;
                agregar(out, referencias, "¿Quién creó " + s.nombre() + "?", null, s.creadores().get(0), pool, "tv:" + s.id() + ":creador");
            }
        }

        // ---- Personas: el papel en cada una de sus películas
        for (var entry : filmografias.entrySet()) {
            List<String> papeles = entry.getValue().stream().map(c -> limpiarPersonaje(texto(c, "character")))
                    .filter(Objects::nonNull).distinct().toList();
            for (Map<?, ?> c : entry.getValue()) {
                String papel = limpiarPersonaje(texto(c, "character"));
                if (papel != null) {
                    agregar(out, referencias, "¿A quién interpretó " + entry.getKey() + " en " + texto(c, "title") + "?", null,
                            papel, papeles, "movie:" + entero(c.get("id")) + ":papel");
                }
            }
        }

        // ---- Fotos
        for (Actor a : actores.values()) {
            if (a.foto() != null) {
                agregar(out, referencias, "¿Quién es?", IMG_PERFIL + a.foto(), a.nombre(), nombresActores, "person:" + a.id() + ":foto");
            }
        }
        return out;
    }

    // ===================================================================
    // Armado de una pregunta
    // ===================================================================
    // Hasta 4 opciones: la correcta más 3 del pool. Sin al menos un distractor
    // no hay pregunta. Una referencia repetida no se agrega dos veces.
    private void agregar(List<PreguntaRequest> out, Set<String> referencias, String texto, String imagen,
                         String correcta, List<String> pool, String referencia) {
        if (correcta == null || correcta.isBlank() || referencias.contains(referencia)) return;
        List<String> distractores = new ArrayList<>();
        for (String s : pool) {
            if (s != null && !s.isBlank() && !s.equalsIgnoreCase(correcta)
                    && distractores.stream().noneMatch(d -> d.equalsIgnoreCase(s))) {
                distractores.add(s);
            }
        }
        if (distractores.isEmpty()) return;
        Collections.shuffle(distractores, random);
        List<String> opciones = new ArrayList<>(distractores.subList(0, Math.min(3, distractores.size())));
        int pos = random.nextInt(opciones.size() + 1);
        opciones.add(pos, correcta);
        referencias.add(referencia);
        out.add(new PreguntaRequest(recortar(texto, 400), imagen, opciones.stream().map(o -> recortar(o, 200)).toList(),
                pos + 1, JuegoPregunta.ORIGEN_TMDB, referencia));
    }

    // Año: la correcta y tres cercanos que no sean futuros.
    private void agregarAnio(List<PreguntaRequest> out, Set<String> referencias, String texto, String anio, String referencia) {
        if (anio == null) return;
        int real = Integer.parseInt(anio);
        int tope = Calendar.getInstance().get(Calendar.YEAR) + 1;
        List<String> cercanos = new ArrayList<>();
        for (int d : new int[]{-1, 1, -2, 2, -3, 3}) {
            int y = real + d;
            if (y > 1880 && y <= tope) cercanos.add(String.valueOf(y));
        }
        agregar(out, referencias, texto, null, anio, cercanos, referencia);
    }

    // ===================================================================
    // Lectura del JSON de TMDb (llega como Map)
    // ===================================================================
    private Peli peli(Map<?, ?> m, boolean conReparto) {
        return new Peli(entero(m.get("id")), texto(m, "title"), anio(texto(m, "release_date")),
                texto(m, "poster_path"), texto(m, "overview"), conReparto);
    }

    private List<Actor> actoresDePelicula(Map<?, ?> creditos) {
        List<Actor> out = new ArrayList<>();
        for (Object o : lista(creditos.get("cast"))) {
            Map<?, ?> c = mapa(o);
            if (texto(c, "name") == null) continue;
            out.add(new Actor(entero(c.get("id")), texto(c, "name"), limpiarPersonaje(texto(c, "character")), texto(c, "profile_path")));
            if (out.size() >= REPARTO) break;
        }
        return out;
    }

    // aggregate_credits de una serie: el personaje está en roles[0].character.
    private List<Actor> actoresDeSerie(Map<?, ?> creditos) {
        List<Actor> out = new ArrayList<>();
        for (Object o : lista(creditos.get("cast"))) {
            Map<?, ?> c = mapa(o);
            if (texto(c, "name") == null) continue;
            String personaje = texto(c, "character");
            List<?> roles = lista(c.get("roles"));
            if (personaje == null && !roles.isEmpty()) personaje = texto(mapa(roles.get(0)), "character");
            out.add(new Actor(entero(c.get("id")), texto(c, "name"), limpiarPersonaje(personaje), texto(c, "profile_path")));
            if (out.size() >= REPARTO) break;
        }
        return out;
    }

    // "Himself", "(voice)", "Narrator (uncredited)" y similares no sirven como pregunta.
    private static String limpiarPersonaje(String p) {
        if (p == null) return null;
        String t = p.trim();
        if (t.isEmpty() || t.contains("(") || t.equalsIgnoreCase("himself") || t.equalsIgnoreCase("herself")
                || t.equalsIgnoreCase("self") || t.toLowerCase(Locale.ROOT).startsWith("self ")) return null;
        return t;
    }

    private static String img(String base, String ruta) {
        return ruta == null ? null : base + ruta;
    }

    private static Map<?, ?> mapa(Object o) {
        if (o instanceof Map<?, ?> m) return m;
        throw new JuegoException(502, "TMDb respondió algo inesperado");
    }

    private static List<?> lista(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static String texto(Map<?, ?> m, String clave) {
        Object v = m.get(clave);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static long entero(Object o) {
        return o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o));
    }

    private static double numero(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static String anio(String fecha) {
        return fecha != null && fecha.matches("\\d{4}-.*") ? fecha.substring(0, 4) : null;
    }

    private static String recortar(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1).replaceAll("\\s+\\S*$", "") + "…";
    }
}