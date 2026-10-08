package com.example.demo.application.services;

import com.example.demo.domain.cinema.Cinema;
import com.example.demo.domain.cinema.CinemaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scraper para agendadecine.com — reemplazó por completo a
 * CarteleraLiveScraperService (carteleraargentina.com.ar, ya borrada).
 * Los comentarios "ESPEJO de..." que quedan abajo son referencia
 * histórica de qué método original inspiró cada uno acá, no una clase
 * que siga existiendo en el proyecto.
 */
@Service
public class AgendaDeCineScraperService {

    private static final String BASE = "https://agendadecine.com";
    private static final Pattern CHUNK_PATTERN =
            Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)</script>", Pattern.DOTALL);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Argentina no cambia de hora (UTC-3 todo el año), pero el servidor de producción corre en UTC:
    // ahí LocalDate.now() ya es "mañana" desde las 21:00 hs de Argentina. Todo lo que dice "hoy" (la
    // bandera esHoy, las funciones de hoy en adelante) tiene que usar la fecha de Argentina.
    private java.time.Clock reloj = java.time.Clock.system(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

    LocalDate hoyEnArgentina() {
        return LocalDate.now(reloj);
    }

    // Solo para las pruebas: fija la hora "actual".
    void usarRelojParaPruebas(java.time.Clock reloj) {
        this.reloj = reloj;
    }
    private final CinemaRepository cinemaRepository;

    public AgendaDeCineScraperService(CinemaRepository cinemaRepository) {
        this.cinemaRepository = cinemaRepository;
    }

    // ===================================================================
    // Funciones de UNA película (ya existente)
    // ===================================================================

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long TTL_MINUTOS = 15;

    private record CacheEntry(List<FuncionAgendaDeCine> funciones, long timestampMillis) {}

    public record CineAgendaDeCine(
            String id,
            String nombre,
            String cadenaId,
            String cadenaNombre,
            String direccion,
            String ciudadId,
            String ciudadNombre,
            String provinciaId,
            String provinciaNombre,
            Double lat,
            Double lng
    ) {}

    public record FuncionAgendaDeCine(
            String id,
            String complexId,
            LocalDate fecha,
            String horario,
            String formato,
            String version,
            String fuente,
            CineAgendaDeCine cine
    ) {}

    public List<FuncionAgendaDeCine> obtenerFunciones(String slugPelicula) throws Exception {
        CacheEntry cacheado = cache.get(slugPelicula);
        if (cacheado != null && System.currentTimeMillis() - cacheado.timestampMillis()
                < TimeUnit.MINUTES.toMillis(vigenciaEnMinutos(cacheado.funciones()))) {
            return cacheado.funciones();
        }

        List<FuncionAgendaDeCine> funciones = descargarFunciones(slugPelicula);
        cache.put(slugPelicula, new CacheEntry(funciones, System.currentTimeMillis()));
        return funciones;
    }

    // Una película con funciones se recuerda 15 minutos. Una que vino SIN funciones, solo 2: puede ser
    // que agendadecine haya respondido mal justo en ese momento, y no conviene dejarla sin funciones
    // un cuarto de hora (antes quedaba "vacía" todo ese tiempo).
    private static long vigenciaEnMinutos(List<FuncionAgendaDeCine> funciones) {
        return funciones.isEmpty() ? 2 : TTL_MINUTOS;
    }

    // Baja la página de la película y saca sus funciones, sin pasar por la caché.
    List<FuncionAgendaDeCine> descargarFunciones(String slugPelicula) throws Exception {
        Document doc = Jsoup.connect(BASE + "/pelicula/" + slugPelicula)
                .userAgent("Mozilla/5.0 (compatible; CinemarketerBot/1.0)")
                .timeout(15000)
                .get();

        String html = doc.outerHtml();
        List<FuncionAgendaDeCine> funciones = new ArrayList<>();

        Matcher m = CHUNK_PATTERN.matcher(html);
        while (m.find()) {
            String chunkTexto = desescaparComoStringJson(m.group(1));
            funciones.addAll(extraerFuncionesDeChunk(chunkTexto));
        }

        return funciones;
    }

    private String desescaparComoStringJson(String chunkEscapado) throws Exception {
        JsonNode nodo = objectMapper.readTree("\"" + chunkEscapado + "\"");
        return nodo.asText();
    }

    private List<FuncionAgendaDeCine> extraerFuncionesDeChunk(String texto) {
        List<FuncionAgendaDeCine> resultado = new ArrayList<>();
        String marcador = "\"cinema\":{";
        int desde = 0;

        while (true) {
            int posCinema = texto.indexOf(marcador, desde);
            if (posCinema == -1) break;

            int inicioObjetoCompleto = buscarInicioObjetoConteniendo(texto, posCinema);
            int finObjetoCompleto = buscarCierreDesde(texto, inicioObjetoCompleto);

            if (inicioObjetoCompleto == -1 || finObjetoCompleto == -1) {
                desde = posCinema + marcador.length();
                continue;
            }

            String bloqueJson = texto.substring(inicioObjetoCompleto, finObjetoCompleto + 1);
            try {
                JsonNode nodo = objectMapper.readTree(bloqueJson);
                resultado.addAll(parsearBloque(nodo));
            } catch (Exception e) {
                // Bloque no es JSON válido por sí solo — se descarta y se sigue.
            }

            desde = finObjetoCompleto + 1;
        }

        return resultado;
    }

    private int buscarInicioObjetoConteniendo(String texto, int posCinema) {
        int profundidad = 0;
        for (int i = posCinema; i >= 0; i--) {
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

    private int buscarCierreArrayDesde(String texto, int inicio) {
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
            if (c == '[') profundidad++;
            else if (c == ']') {
                profundidad--;
                if (profundidad == 0) return i;
            }
        }
        return -1;
    }

    private List<FuncionAgendaDeCine> parsearBloque(JsonNode nodo) {
        List<FuncionAgendaDeCine> resultado = new ArrayList<>();
        JsonNode cineNodo = nodo.get("cinema");
        if (cineNodo == null) return resultado;

        CineAgendaDeCine cine = new CineAgendaDeCine(
                textoOrNull(cineNodo, "id"),
                textoOrNull(cineNodo, "name"),
                textoOrNull(cineNodo, "chainId"),
                textoOrNull(cineNodo, "chainName"),
                textoOrNull(cineNodo, "address"),
                textoOrNull(cineNodo, "cityId"),
                textoOrNull(cineNodo, "cityName"),
                textoOrNull(cineNodo, "provinceId"),
                textoOrNull(cineNodo, "provinceName"),
                dobleOrNull(cineNodo, "latitude"),
                dobleOrNull(cineNodo, "longitude")
        );

        Iterator<Map.Entry<String, JsonNode>> campos = nodo.fields();
        while (campos.hasNext()) {
            Map.Entry<String, JsonNode> campo = campos.next();
            if (campo.getKey().equals("cinema")) continue;
            if (!campo.getValue().isArray()) continue;

            for (JsonNode funcionNodo : campo.getValue()) {
                String fecha = textoOrNull(funcionNodo, "date");
                if (fecha == null) continue;
                resultado.add(new FuncionAgendaDeCine(
                        textoOrNull(funcionNodo, "id"),
                        textoOrNull(funcionNodo, "complexId"),
                        LocalDate.parse(fecha),
                        textoOrNull(funcionNodo, "time"),
                        textoOrNull(funcionNodo, "format"),
                        textoOrNull(funcionNodo, "version"),
                        textoOrNull(funcionNodo, "source"),
                        cine
                ));
            }
        }

        return resultado;
    }

    private String textoOrNull(JsonNode nodo, String campo) {
        JsonNode v = nodo.get(campo);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private Double dobleOrNull(JsonNode nodo, String campo) {
        JsonNode v = nodo.get(campo);
        return (v == null || v.isNull()) ? null : v.asDouble();
    }

    public List<FuncionAgendaDeCine> obtenerFuncionesPorProvincia(String slugPelicula, String provincia, String ciudad) throws Exception {
        List<FuncionAgendaDeCine> todas = obtenerFunciones(slugPelicula);
        String provinciaNorm = normalizar(provincia);
        String ciudadNorm = (ciudad == null || ciudad.isBlank()) ? null : normalizar(ciudad);

        return todas.stream()
                .filter(f -> f.cine().provinciaNombre() != null
                        && normalizar(f.cine().provinciaNombre()).equals(provinciaNorm))
                .filter(f -> ciudadNorm == null
                        || (f.cine().ciudadNombre() != null && normalizar(f.cine().ciudadNombre()).equals(ciudadNorm)))
                .toList();
    }

    private String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u");
    }

    // ===================================================================
    // ESPEJO de CarteleraLiveScraperService.obtenerPeliculasEnCartelera()
    // / descubrirPeliculas() — misma firma, mismo TTL (30 min), mismo
    // "nunca tira excepción hacia afuera" (si falla el scraping, devuelve
    // la cache vieja o lista vacía en vez de romper al que llama).
    //
    // A diferencia del original (que necesitaba scrapear ~100+ fichas de
    // cine en paralelo más una segunda pasada por película para sacar el
    // género, porque carteleraargentina no tenía un listado único), acá
    // agendadecine.com/en-cartel trae todo en un solo pedido — mismo
    // resultado hacia afuera, bastante más liviano por dentro.
    // ===================================================================

    public record PeliculaEnCartelera(String slug, String titulo, String poster, List<String> genero, String sinopsis) {
        // El constructor de siempre, sin sinopsis, para quien arma la película con 4 datos.
        public PeliculaEnCartelera(String slug, String titulo, String poster, List<String> genero) {
            this(slug, titulo, poster, genero, null);
        }
    }

    private List<PeliculaEnCartelera> cachePeliculas = null;
    private LocalDateTime cachePeliculasExpira = null;
    private final ReentrantLock lockPeliculas = new ReentrantLock();

    public List<PeliculaEnCartelera> obtenerPeliculasEnCartelera() {
        lockPeliculas.lock();
        try {
            if (cachePeliculas != null && cachePeliculasExpira != null && LocalDateTime.now().isBefore(cachePeliculasExpira)) {
                return cachePeliculas;
            }
            try {
                cachePeliculas = descubrirPeliculasEnCartel();
            } catch (Exception e) {
                // Mismo criterio que el original: un fallo acá no debe
                // romper al que llama — se devuelve la cache vieja si
                // había, o vacía si era la primera vez.
                cachePeliculas = (cachePeliculas != null) ? cachePeliculas : List.of();
            }
            cachePeliculasExpira = LocalDateTime.now().plusMinutes(30);
            return cachePeliculas;
        } finally {
            lockPeliculas.unlock();
        }
    }

    // Sinopsis de una película en cartelera, buscada por el título que traen las funciones
    // (sin importar mayúsculas ni tildes). null si no se encuentra o si agendadecine no la tiene.
    public String obtenerSinopsis(String titulo) {
        if (titulo == null) return null;
        String buscado = ZonaGeografica.normalizar(titulo);
        for (PeliculaEnCartelera p : obtenerPeliculasEnCartelera()) {
            if (p.sinopsis() != null && ZonaGeografica.normalizar(p.titulo()).equals(buscado)) {
                return p.sinopsis();
            }
        }
        return null;
    }

    private List<PeliculaEnCartelera> descubrirPeliculasEnCartel() throws Exception {
        Document doc = Jsoup.connect(BASE + "/en-cartel")
                .userAgent("Mozilla/5.0 (compatible; CinemarketerBot/1.0)")
                .timeout(15000)
                .get();

        StringBuilder textoCompleto = new StringBuilder();
        Matcher m = CHUNK_PATTERN.matcher(doc.outerHtml());
        while (m.find()) {
            textoCompleto.append(desescaparComoStringJson(m.group(1)));
        }
        String texto = textoCompleto.toString();

        String marcador = "\"movies\":[";
        int pos = texto.indexOf(marcador);
        if (pos == -1) return List.of();

        int inicioArray = pos + marcador.length() - 1;
        int finArray = buscarCierreArrayDesde(texto, inicioArray);
        if (finArray == -1) return List.of();

        JsonNode movies = objectMapper.readTree(texto.substring(inicioArray, finArray + 1));
        return peliculasDesdeJson(movies);
    }

    // Arma la lista de películas a partir del arreglo "movies" que trae /en-cartel.
    List<PeliculaEnCartelera> peliculasDesdeJson(JsonNode movies) {
        List<PeliculaEnCartelera> resultado = new ArrayList<>();
        for (JsonNode movie : movies) {
            String slug = textoOrNull(movie, "slug");
            if (slug == null) continue;

            JsonNode release = movie.get("release");
            String titulo = release != null ? textoOrNull(release, "localTitle") : null;
            if (titulo == null) titulo = textoOrNull(movie, "originalTitle");

            String poster = release != null ? textoOrNull(release, "posterUrl") : null;

            List<String> generos = new ArrayList<>();
            JsonNode generosNodo = movie.get("genres");
            if (generosNodo != null && generosNodo.isArray()) {
                for (JsonNode g : generosNodo) generos.add(g.asText());
            }

            // La sinopsis viene en "release", junto al póster y el tráiler; si no está ahí, se
            // busca en la película. Vacía o en blanco se guarda como "sin sinopsis".
            String sinopsis = release != null ? textoOrNull(release, "synopsis") : null;
            if (sinopsis == null || sinopsis.isBlank()) sinopsis = textoOrNull(movie, "synopsis");
            sinopsis = (sinopsis == null || sinopsis.isBlank()) ? null : sinopsis.trim();

            resultado.add(new PeliculaEnCartelera(slug, titulo, poster, generos, sinopsis));
        }
        return resultado;
    }

    // ===================================================================
    // ESPEJO de CarteleraLiveScraperService.obtenerTodasLasFuncionesCacheadas()
    // — mismo TTL (30 min), mismo paralelismo (3 threads), mismo criterio
    // de "una película que falla no tira abajo el resto".
    // ===================================================================

    private volatile Map<String, List<FuncionAgendaDeCine>> cacheFuncionesPorSlug = null;
    private LocalDateTime cacheFuncionesExpira = null;
    private final ReentrantLock lockFunciones = new ReentrantLock();

    private Map<String, List<FuncionAgendaDeCine>> obtenerTodasLasFuncionesCacheadas() {
        lockFunciones.lock();
        try {
            if (cacheFuncionesPorSlug != null && cacheFuncionesExpira != null && LocalDateTime.now().isBefore(cacheFuncionesExpira)) {
                return cacheFuncionesPorSlug;
            }

            List<PeliculaEnCartelera> peliculas = obtenerPeliculasEnCartelera();

            ExecutorService executor = Executors.newFixedThreadPool(3);
            Map<String, Future<List<FuncionAgendaDeCine>>> futures = new LinkedHashMap<>();
            for (PeliculaEnCartelera p : peliculas) {
                futures.put(p.slug(), executor.submit(() -> obtenerFunciones(p.slug())));
            }

            Map<String, List<FuncionAgendaDeCine>> resultado = new LinkedHashMap<>();
            for (var entry : futures.entrySet()) {
                try {
                    resultado.put(entry.getKey(), entry.getValue().get(20, TimeUnit.SECONDS));
                } catch (Exception e) {
                    // Una película que falla no tira abajo el resto — mismo criterio que el original.
                }
            }
            executor.shutdown();

            cacheFuncionesPorSlug = resultado;
            cacheFuncionesExpira = LocalDateTime.now().plusMinutes(30);
            return resultado;
        } finally {
            lockFunciones.unlock();
        }
    }

    // Al arrancar se cargan las funciones en segundo plano, para que la lista de cadenas
    // (que usa lo que ya está cargado) no tenga que esperar la primera carga.
    @jakarta.annotation.PostConstruct
    void precargarFunciones() {
        Thread hilo = new Thread(() -> {
            try {
                obtenerTodasLasFuncionesCacheadas();
            } catch (Exception e) {
                // Si falla, la próxima pantalla que necesite las funciones vuelve a intentarlo.
            }
        }, "precarga-funciones");
        hilo.setDaemon(true);
        hilo.start();
    }

    // Mismo record que tenía CarteleraLiveScraperService (ya borrada) —
    // vive acá ahora porque el contrato hacia el controller y hacia
    // RecomendacionEspirituService depende de esta forma exacta de dato,
    // no de en qué archivo esté definida.
    public record FuncionScrapeada(
            String peliculaTitulo, String provincia, String cineNombre,
            LocalDate dia, boolean esHoy, LocalTime horario, String formato, String idioma,
            String poster
    ) {}

    // ===================================================================
    // ESPEJO de CarteleraLiveScraperService.organizarSalida() — mismo
    // filtro (cines de la geografía pedida, misma fecha, misma franja
    // horaria), mismo orden de salida, MISMO tipo de retorno
    // (FuncionScrapeada, definido arriba) para que el controller y el
    // front no necesiten cambiar nada en la forma del dato — solo cambia
    // el origen interno: el cruce geográfico ahora es contra
    // Cinema.agendaDeCineComplexId (estable) en vez de Cinema.scrapedName
    // (texto frágil).
    // ===================================================================

    // Funciones de una búsqueda por zona, y si hubo que ampliar la zona pedida: si
    // en la localidad (ej. un barrio) no hay funciones, se devuelven las de toda
    // la provincia — en CABA, las de toda la ciudad. Ver ZonaGeografica.
    public record FuncionesDeZona(List<FuncionScrapeada> funciones, boolean zonaAmpliada) {}

    public List<FuncionScrapeada> organizarSalida(
            String provincia, String localidad, LocalDate fecha, String horario) {
        return organizarSalidaConZona(provincia, localidad, fecha, horario).funciones();
    }

    public FuncionesDeZona organizarSalidaConZona(
            String provincia, String localidad, LocalDate fecha, String horario) {

        boolean hayLocalidad = localidad != null && !localidad.isBlank();
        List<Cinema> delaProvincia = ZonaGeografica.cinesDeLaProvincia(cinemaRepository.findByActiveTrue(), provincia);

        var zona = ZonaGeografica.conAmpliacion(hayLocalidad,
                () -> organizarSalidaEn(ZonaGeografica.cinesDeLaLocalidad(delaProvincia, localidad), fecha, horario),
                () -> organizarSalidaEn(delaProvincia, fecha, horario));
        return new FuncionesDeZona(zona.items(), zona.ampliada());
    }

    private List<FuncionScrapeada> organizarSalidaEn(List<Cinema> cines, LocalDate fecha, String horario) {

        Set<String> complexIdsMatch = cines.stream()
                .map(Cinema::getAgendaDeCineComplexId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> tituloPorSlug = new HashMap<>();
        Map<String, String> posterPorSlug = new HashMap<>();
        for (PeliculaEnCartelera p : obtenerPeliculasEnCartelera()) {
            tituloPorSlug.put(p.slug(), p.titulo());
            posterPorSlug.put(p.slug(), p.poster());
        }

        List<FuncionScrapeada> resultado = new ArrayList<>();
        for (var entry : obtenerTodasLasFuncionesCacheadas().entrySet()) {
            String slug = entry.getKey();
            String tituloPelicula = tituloPorSlug.getOrDefault(slug, slug);
            String poster = posterPorSlug.get(slug);

            for (FuncionAgendaDeCine f : entry.getValue()) {
                if (!complexIdsMatch.contains(f.complexId())) continue;
                if (!f.fecha().equals(fecha)) continue;
                if (!coincideFranjaHoraria(f.horario(), horario)) continue;

                LocalTime horaParsed;
                try {
                    horaParsed = LocalTime.parse(f.horario());
                } catch (Exception e) {
                    continue;
                }

                resultado.add(new FuncionScrapeada(
                        tituloPelicula,
                        f.cine().provinciaNombre(),
                        f.cine().nombre(),
                        f.fecha(),
                        f.fecha().equals(hoyEnArgentina()),
                        horaParsed,
                        f.formato(),
                        f.version(),
                        poster
                ));
            }
        }

        resultado.sort(Comparator.comparing(FuncionScrapeada::horario));
        return resultado;
    }

    private boolean coincideFranjaHoraria(String horarioTexto, String franja) {
        if (franja == null || franja.isBlank()) return true;
        try {
            int h = LocalTime.parse(horarioTexto).getHour();
            return switch (franja) {
                case "manana" -> h < 12;
                case "tarde" -> h >= 12 && h < 19;
                case "noche" -> h >= 19;
                default -> true;
            };
        } catch (Exception e) {
            return true;
        }
    }

    // ===================================================================
    // ESPEJO de CarteleraLiveScraperService.obtenerFuncionesPorPeliculaYGeografia()
    // — mismo cruce contra Cinema (ahora por complexId), para UNA película
    // puntual en vez de todas. Mismo tipo de retorno que organizarSalida,
    // por la misma razón: que el controller no necesite cambiar forma.
    // ===================================================================

    public List<FuncionScrapeada> obtenerFuncionesPorPeliculaYGeografia(
            String slug, String provincia, String localidad) throws Exception {
        return obtenerFuncionesPorPeliculaYGeografiaConZona(slug, provincia, localidad).funciones();
    }

    // Funciones de una película en una zona. Si en la localidad no hay ninguna (y se pidió una localidad),
    // devuelve las de toda la provincia y lo avisa. La lógica de la ampliación está acá, a la vista: se
    // apoya solo en los cines de la provincia y de la localidad y en el cruce por complexId.
    public FuncionesDeZona obtenerFuncionesPorPeliculaYGeografiaConZona(
            String slug, String provincia, String localidad) throws Exception {

        boolean hayLocalidad = localidad != null && !localidad.isBlank();
        List<Cinema> delaProvincia = ZonaGeografica.cinesDeLaProvincia(cinemaRepository.findByActiveTrue(), provincia);

        String titulo = slug;
        String poster = null;
        for (PeliculaEnCartelera p : obtenerPeliculasEnCartelera()) {
            if (p.slug().equals(slug)) {
                titulo = p.titulo();
                poster = p.poster();
                break;
            }
        }

        // Se bajan una sola vez: sirven para la localidad y, si hace falta, para la provincia.
        List<FuncionAgendaDeCine> deLaPelicula = funcionesDeLaPelicula(slug);

        // 1) La localidad pedida (o toda la provincia, si no se pidió ninguna).
        List<Cinema> cinesDeLaZona = hayLocalidad
                ? ZonaGeografica.cinesDeLaLocalidad(delaProvincia, localidad)
                : delaProvincia;
        List<FuncionScrapeada> enLaZona = convertirFuncionesDePelicula(deLaPelicula, cinesDeLaZona, titulo, poster);
        if (!enLaZona.isEmpty() || !hayLocalidad) return new FuncionesDeZona(enLaZona, false);

        // 2) La localidad no tiene funciones de esta película: se amplía a toda la provincia y se avisa.
        List<FuncionScrapeada> enLaProvincia = convertirFuncionesDePelicula(deLaPelicula, delaProvincia, titulo, poster);
        return new FuncionesDeZona(enLaProvincia, !enLaProvincia.isEmpty());
    }

    // Funciones de UNA película para "Ya sé qué quiero ver". Se prefiere lo que ya está en la caché
    // general —la misma que usan "Por cadena", "Hoy cerca tuyo" y "Organizar una salida"—, así un cine
    // no tiene funciones en un atajo y no en otro. Si la caché general no tiene la película (o venció) se
    // baja la página; y si esa bajada falla o viene vacía, se usa lo que haya en la caché general aunque
    // esté vencido. Solo si no hay nada de nada, el error se mantiene.
    private List<FuncionAgendaDeCine> funcionesDeLaPelicula(String slug) throws Exception {
        Map<String, List<FuncionAgendaDeCine>> general = cacheFuncionesPorSlug;   // sin cargar ni esperar
        LocalDateTime vence = cacheFuncionesExpira;
        List<FuncionAgendaDeCine> deLaGeneral = general == null ? null : general.get(slug);
        boolean generalVigente = vence != null && LocalDateTime.now().isBefore(vence);
        if (generalVigente && deLaGeneral != null && !deLaGeneral.isEmpty()) return deLaGeneral;

        Exception fallo = null;
        List<FuncionAgendaDeCine> propias = List.of();
        try {
            propias = obtenerFunciones(slug);
        } catch (Exception e) {
            fallo = e;
        }
        if (!propias.isEmpty()) return propias;
        if (deLaGeneral != null && !deLaGeneral.isEmpty()) return deLaGeneral;   // vencida, pero mejor que nada
        if (fallo != null) throw fallo;
        return propias;
    }

    // Las funciones de la película que se dan en los cines indicados, de hoy en adelante.
    private List<FuncionScrapeada> convertirFuncionesDePelicula(
            List<FuncionAgendaDeCine> funciones, List<Cinema> cines, String titulo, String poster) {

        Set<String> idsDeLosCines = cines.stream()
                .map(Cinema::getAgendaDeCineComplexId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        LocalDate hoy = hoyEnArgentina();

        List<FuncionScrapeada> resultado = new ArrayList<>();
        for (FuncionAgendaDeCine f : funciones) {
            if (f.fecha() == null || f.fecha().isBefore(hoy)) continue;   // de hoy en adelante
            if (!idsDeLosCines.contains(f.complexId())) continue;          // en los cines de la zona

            LocalTime hora;
            try {
                hora = LocalTime.parse(f.horario());
            } catch (Exception e) {
                continue;
            }

            resultado.add(new FuncionScrapeada(
                    titulo,
                    f.cine().provinciaNombre(),
                    f.cine().nombre(),
                    f.fecha(),
                    f.fecha().equals(hoy),
                    hora,
                    f.formato(),
                    f.version(),
                    poster
            ));
        }
        return resultado;
    }

    // ===================================================================
    // ESPEJO de CarteleraLiveScraperService.obtenerCadenasDisponibles() /
    // obtenerCinesPorCadena() — el original tenía que DERIVAR la cadena
    // del nombre de cine a mano (mapa CADENAS_CONOCIDAS + derivarCadena())
    // porque carteleraargentina no la daba como dato. Acá la cadena ya es
    // un campo real sincronizado en Cinema.chain, así que estos dos
    // consultan directo el directorio local — sin tocar la red, sin
    // depender de que haya scraping de funciones corriendo.
    // ===================================================================

    // Cadena a la que se muestra cada cine cuando agendadecine lo agrupa mal
    // (ej. Cinema Adrogué dentro de Atlas) o bajo un nombre que no es una cadena
    // ("Lumiere" es una empresa que programa cines, no una cadena). Se compara
    // con el nombre del cine sin tildes y en minúsculas, y gana la primera regla
    // que coincide. Cada regla corresponde a una marca verificada en su sitio
    // oficial. Va en el código y no en la base porque la sincronización del
    // directorio pisa la cadena de cada cine en cada corrida.
    private record ReglaDeCadena(String cadena, java.util.function.Predicate<String> coincide) {}

    // Espacios INCAA comprobados fuera de agendadecine (listado de Espacios INCAA,
    // programa oficial, notas de prensa y del Gobierno de Río Negro). Nombre del cine
    // sin tildes y en minúsculas. Para sumar uno, se agrega acá.
    private static final Set<String> CINES_INCAA_VERIFICADOS = Set.of(
            "cine gaumont",                          // sala propiedad del INCAA
            "centro cultural florencio constantino", // sala N° 50 del programa (Bragado)
            "cine teatro tapalque",
            "cine teatro espanol cinco saltos",
            "cine teatro circulo italiano",          // Villa Regina
            "centro cultural cotesma",               // Cine Amancay, San Martín de los Andes
            "cine arte cordoba");

    private static final List<ReglaDeCadena> REGLAS_DE_CADENA = List.of(
            new ReglaDeCadena("Cine París", n -> n.startsWith("cine paris ")),
            new ReglaDeCadena("Cines Dino", n -> n.startsWith("cines dino ")),
            new ReglaDeCadena("Tu Cine", n -> n.startsWith("tu cine ")),
            new ReglaDeCadena("De la Costa", n -> n.startsWith("de la costa ")),
            // Costa atlántica (cineslacosta.com.ar): marca distinta de "De la Costa" (NEA).
            new ReglaDeCadena("Cines La Costa", n -> n.equals("cine yanel") || n.equals("cine arenas")
                    || n.equals("cine california") || n.equals("cine oasis")),
            new ReglaDeCadena("SudCinemas", n -> n.startsWith("sudcinemas ")),
            new ReglaDeCadena("Flix Cinema", n -> n.startsWith("flix cinema ")),
            new ReglaDeCadena("Tadicor", n -> n.startsWith("cines tadicor ")),
            new ReglaDeCadena("Cines Santa Rosa", n -> n.equals("cine amadeus") || n.equals("cine milenium")),
            // Va antes que "Cine Rex": La Banda y Termas comparten el sitio nuevocinerex.com.ar.
            new ReglaDeCadena("Nuevo Cine Rex", n -> n.equals("cine rex la banda") || n.equals("nuevo cine rex termas")),
            // Va antes que "Cine Rex": el sitio de Cine Círculo lista General Roca, y el
            // directorio asigna ese mismo sitio (cinecirculo.com.ar) a "Cine Rex Gral. Roca",
            // el único cine comercial de esa ciudad.
            new ReglaDeCadena("Cine Círculo", n -> n.startsWith("cine circulo ") || n.equals("cine rex gral. roca")),
            new ReglaDeCadena("Cine Rex", n -> n.startsWith("cine rex ")),
            new ReglaDeCadena("Cineclub Center", n -> n.startsWith("cineclub center")),
            // Solo los que se pueden comprobar como Espacios INCAA: los que lo dicen en el
            // nombre y los de CINES_INCAA_VERIFICADOS. El resto de lo que agendadecine
            // etiqueta "Incaa" se muestra como Independiente (ver cadenaCorregida).
            new ReglaDeCadena("Espacios INCAA", n -> n.startsWith("espacio incaa ") || CINES_INCAA_VERIFICADOS.contains(n)),
            // La sala IMAX de Showcase Norte (Vicente López). Showcase figura en el directorio
            // como "NAI": con ese nombre queda junto a sus otras sedes (el front la muestra como Showcase).
            new ReglaDeCadena("NAI", n -> n.equals("imax theater")));

    // Cines que son una cadena en sí mismos (una sola sucursal): se muestran
    // como cadena con su propio nombre. Nombre del cine sin tildes y en minúsculas.
    private static final Set<String> CINES_CADENA_PROPIA = Set.of(
            "cinema adrogue", "cine gran pampa", "cine fantasio", "cine general paz",
            "cine opera salta", "cinema devoto", "nuevo monumental", "play cinema",
            "annuar shopping cines", "centro cultural cine zurro", "cine arte cacodelphia", "cine america",
            "cine campana", "cine cosmos uba", "cine el cairo", "cine lorca",
            "cines fenix", "cines ocean", "cines pixel", "malba");


    // Lo que agendadecine agrupa bajo etiquetas que no son cadenas ("Lumiere" es una
    // empresa que programa cines; "Independiente" e "Incaa" son etiquetas sueltas) y
    // que no se pudo asignar a una marca ni comprobar como Espacio INCAA, se muestra
    // junto como una sola cadena.
    private static final String CADENA_OTROS_CINES = "Otros cines";
    private static final Set<String> ETIQUETAS_QUE_NO_SON_CADENA = Set.of("lumiere", "independiente", "incaa", "imax");

    // Cadena "de siempre" de un cine, según agendadecine y nuestras correcciones. No
    // depende de si hoy hay funciones.
    public String cadenaBaseDe(Cinema cine) {
        return cadenaCorregida(cine.getName(), cine.getChain());
    }

    // Cadena con la que se muestra un cine en "Por cadena de cine": solo se muestran las
    // cadenas que tienen al menos un cine con funciones de hoy en adelante, y esa cadena
    // conserva TODAS sus sucursales, tengan o no funciones. Los cines de las demás cadenas
    // se muestran en "Otros cines". Si no hay datos de funciones, no se filtra nada.
    public String cadenaDe(Cinema cine) {
        String base = cadenaBaseDe(cine);
        if (CADENA_OTROS_CINES.equals(base)) return base;
        Set<String> conFunciones = cadenasConFunciones();
        return (conFunciones == null || conFunciones.contains(base)) ? base : CADENA_OTROS_CINES;
    }

    // Cadenas (de siempre) con al menos un cine con funciones de hoy en adelante. Se
    // recalcula como máximo cada 10 minutos. null = no hay datos de funciones: en ese caso
    // no se filtra, para que una caída de agendadecine no mande todos los cines a "Otros
    // cines".
    private volatile Set<String> cadenasConFuncionesCache = null;
    private volatile boolean cadenasConFuncionesCalculadas = false;
    private volatile LocalDateTime cadenasConFuncionesExpira = null;

    private Set<String> cadenasConFunciones() {
        LocalDateTime ahora = LocalDateTime.now();
        if (cadenasConFuncionesCalculadas && cadenasConFuncionesExpira != null
                && ahora.isBefore(cadenasConFuncionesExpira)) {
            return cadenasConFuncionesCache;
        }
        Set<String> calculado = null;
        try {
            // Recién arrancó y todavía no hay funciones cargadas: se espera (hasta 20 segundos) a
            // que termine la carga inicial en vez de mostrar todas las cadenas sin filtrar.
            if (cacheFuncionesPorSlug == null) {
                try {
                    if (lockFunciones.tryLock(20, TimeUnit.SECONDS)) lockFunciones.unlock();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Map<String, Integer> porComplejo = funcionesVigentesPorComplejo();
            if (!porComplejo.isEmpty()) {
                calculado = new HashSet<>();
                for (Cinema cine : cinemaRepository.findByActiveTrue()) {
                    String complejo = cine.getAgendaDeCineComplexId();
                    if (complejo != null && porComplejo.getOrDefault(complejo, 0) > 0) {
                        calculado.add(cadenaBaseDe(cine));
                    }
                }
            }
        } catch (Exception e) {
            calculado = null; // sin datos de funciones: no se filtra
        }
        cadenasConFuncionesCache = calculado;
        cadenasConFuncionesCalculadas = true;
        // Sin datos se reintenta casi enseguida (3 segundos); con datos se reusa 10 minutos. El
        // plazo corre desde que terminó el cálculo y no desde que empezó, porque el cálculo puede
        // haber esperado la carga inicial.
        cadenasConFuncionesExpira = LocalDateTime.now().plusSeconds(calculado == null ? 3 : 600);
        return calculado;
    }

    // Cantidad de funciones de hoy en adelante por id de complejo de agendadecine, según lo
    // que YA está cargado en la caché de funciones. No dispara la carga ni espera por ella:
    // la lista de cadenas no puede quedar atada a una carga lenta o fallida de las funciones
    // de todas las películas. Si todavía no hay nada cargado devuelve vacío, y entonces no
    // se filtra ninguna cadena.
    private Map<String, Integer> funcionesVigentesPorComplejo() {
        Map<String, List<FuncionAgendaDeCine>> cargadas = cacheFuncionesPorSlug; // sin lock y sin cargar
        Map<String, Integer> porComplejo = new HashMap<>();
        if (cargadas == null) return porComplejo;
        LocalDate hoy = hoyEnArgentina();
        for (List<FuncionAgendaDeCine> funciones : cargadas.values()) {
            for (FuncionAgendaDeCine f : funciones) {
                if (f.fecha() == null || f.fecha().isBefore(hoy)) continue;
                porComplejo.merge(f.complexId(), 1, Integer::sum);
            }
        }
        return porComplejo;
    }

    private String cadenaCorregida(String nombreCine, String cadenaAgendaDeCine) {
        String nombre = ZonaGeografica.normalizar(nombreCine);
        for (ReglaDeCadena regla : REGLAS_DE_CADENA) {
            if (regla.coincide().test(nombre)) return regla.cadena();
        }
        if (CINES_CADENA_PROPIA.contains(nombre)) return nombreCine.trim();
        // Cines sin cadena y etiquetas que no son cadenas (Lumiere, Independiente, Incaa)
        // que no se pudo asignar a una marca ni comprobar como Espacio INCAA.
        if (cadenaAgendaDeCine == null || cadenaAgendaDeCine.isBlank()
                || ETIQUETAS_QUE_NO_SON_CADENA.contains(cadenaAgendaDeCine.trim().toLowerCase())) {
            return CADENA_OTROS_CINES;
        }
        return cadenaAgendaDeCine;
    }

    // Nombre con que se MUESTRA un cine en la lista de sucursales cuando el del
    // directorio no es el que usa la cadena en su sitio oficial, o no alcanza para
    // distinguirlo dentro de ella. El nombre del directorio sigue siendo el que se
    // usa para buscar sus funciones. Clave: "cadena|nombre del cine", los dos sin
    // tildes y en minúsculas. Lleva la cadena porque hay nombres que se repiten en
    // otras ("Cine San Martín" es de Concepción del Uruguay y también de Las Flores).
    private static final Map<String, String> NOMBRE_VISIBLE = Map.ofEntries(
            Map.entry("cineclub center|cineclub center", "Cineclub Center Metán"),
            Map.entry("cinema concept|cine cervantes", "Cinema Concept Mendoza"),
            Map.entry("cinema concept|cine coliseo", "Cinema Concept Trelew"),
            Map.entry("cinema concept|cine gran libertad", "Cinema Concept Chajarí"),
            Map.entry("cinema concept|cine san martin", "Cinema Concept Concepción del Uruguay"),
            Map.entry("cinema concept|cine teatro opera", "Cinema Concept Paso de los Libres"),
            Map.entry("cinema concept|cinema gualeguaychu", "Cinema Concept Gualeguaychú"),
            // El Sunstar de Paraná funciona en el shopping La Paz y el directorio lo carga con ese nombre.
            Map.entry("cinemas sunstar|la paz cinema", "Sunstar Paraná"));

    public String nombreVisibleDe(Cinema cine) {
        String clave = ZonaGeografica.normalizar(cadenaBaseDe(cine)) + "|" + ZonaGeografica.normalizar(cine.getName());
        String visible = NOMBRE_VISIBLE.get(clave);
        return visible != null ? visible : cine.getName();
    }

    // true si ya se sabe qué cadenas tienen funciones (hay funciones cargadas); false mientras se
    // cargan por primera vez o si no se pudieron cargar.
    public boolean cadenasListas() {
        return cadenasConFunciones() != null;
    }

    public List<String> obtenerCadenasDisponibles() {
        // Sin datos de funciones no se sabe qué cadenas tienen funciones: no se muestra ninguna
        // (el front avisa que no pudo cargarlas) en vez de mostrarlas todas sin filtrar.
        if (cadenasConFunciones() == null) return List.of();
        return cinemaRepository.findByActiveTrue().stream()
                .map(this::cadenaDe)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .distinct()
                // "Otros cines" es el grupo de lo que no es una cadena: va al final.
                .sorted(Comparator.comparing((String nombre) -> nombre.equals(CADENA_OTROS_CINES))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    public List<Cinema> obtenerCinesPorCadena(String cadena) {
        return cinemaRepository.findByActiveTrue().stream()
                .filter(c -> cadena.equalsIgnoreCase(cadenaDe(c)))
                .toList();
    }

    // ===================================================================
    // ESPEJO de CarteleraLiveScraperService.obtenerFuncionesPorCadena() /
    // obtenerFuncionesPorCine() — estos SÍ necesitan las funciones
    // scrapeadas (no alcanza con el directorio), así que siguen el mismo
    // patrón que organizarSalida: cruzar FuncionAgendaDeCine, armar
    // título/póster por slug, y convertir recién al final a
    // FuncionScrapeada para no romper el contrato hacia el controller.
    // ===================================================================

    public List<FuncionScrapeada> obtenerFuncionesPorCadena(String cadena) {
        return filtrarFuncionesConvertidas(f -> cadena.equalsIgnoreCase(
                cadenaCorregida(f.cine().nombre(), f.cine().cadenaNombre())));
    }

    public List<FuncionScrapeada> obtenerFuncionesPorCine(String nombreCine) {
        return obtenerFuncionesPorCine(nombreCine, null);
    }

    // Funciones de una sucursal. Se cruzan por el id de complejo de agendadecine —igual que en el
    // resto de las pantallas— y no por el nombre: el nombre del directorio y el que trae cada
    // función pueden no coincidir, y entonces la lista salía vacía aunque el cine tuviera
    // funciones. Si llega el id del cine (lo manda "Por cadena de cine") se usa ese, que no se
    // confunde con otro cine de nombre parecido; si no, se busca por nombre, como antes.
    public List<FuncionScrapeada> obtenerFuncionesPorCine(String nombreCine, Long cineId) {
        Set<String> complejos = cinemaRepository.findByActiveTrue().stream()
                .filter(c -> cineId != null ? cineId.equals(c.getId()) : nombreCine.equalsIgnoreCase(c.getName()))
                .map(Cinema::getAgendaDeCineComplexId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return filtrarFuncionesConvertidas(f -> complejos.contains(f.complexId())
                || (cineId == null && nombreCine.equalsIgnoreCase(f.cine().nombre())));
    }

    private List<FuncionScrapeada> filtrarFuncionesConvertidas(Predicate<FuncionAgendaDeCine> filtro) {
        Map<String, String> tituloPorSlug = new HashMap<>();
        Map<String, String> posterPorSlug = new HashMap<>();
        for (PeliculaEnCartelera p : obtenerPeliculasEnCartelera()) {
            tituloPorSlug.put(p.slug(), p.titulo());
            posterPorSlug.put(p.slug(), p.poster());
        }

        List<FuncionScrapeada> resultado = new ArrayList<>();
        for (var entry : obtenerTodasLasFuncionesCacheadas().entrySet()) {
            String slug = entry.getKey();
            String tituloPelicula = tituloPorSlug.getOrDefault(slug, slug);
            String poster = posterPorSlug.get(slug);

            for (FuncionAgendaDeCine f : entry.getValue()) {
                if (!filtro.test(f)) continue;

                LocalTime hora;
                try {
                    hora = LocalTime.parse(f.horario());
                } catch (Exception e) {
                    continue;
                }

                resultado.add(new FuncionScrapeada(
                        tituloPelicula,
                        f.cine().provinciaNombre(),
                        f.cine().nombre(),
                        f.fecha(),
                        f.fecha().equals(hoyEnArgentina()),
                        hora,
                        f.formato(),
                        f.version(),
                        poster
                ));
            }
        }

        resultado.sort(Comparator.comparing(FuncionScrapeada::horario));
        return resultado;
    }

    // Diagnóstico: formatos distintos que llegan en las funciones, por
    // cadena, con la cantidad de funciones de cada uno. Sirve para armar el
    // cruce entre los formatos de agendadecine y los de la fuente de
    // precios con datos reales y no con nombres adivinados.
    public Map<String, Map<String, Long>> obtenerFormatosPorCadena() {
        Map<String, Map<String, Long>> resultado = new TreeMap<>();
        for (List<FuncionAgendaDeCine> funciones : obtenerTodasLasFuncionesCacheadas().values()) {
            for (FuncionAgendaDeCine f : funciones) {
                String cadena = f.cine().cadenaNombre() == null ? "(sin cadena)" : f.cine().cadenaNombre();
                String formato = f.formato() == null ? "(sin formato)" : f.formato();
                resultado.computeIfAbsent(cadena, k -> new TreeMap<>()).merge(formato, 1L, Long::sum);
            }
        }
        return resultado;
    }
}