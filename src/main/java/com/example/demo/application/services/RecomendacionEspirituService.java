package com.example.demo.application.services;

import com.example.demo.application.dtos.GenreScoreDto;
import com.example.demo.application.dtos.RecomendacionEspirituDto;
import com.example.demo.application.dtos.RecomendacionItemDto;
import com.example.demo.domain.review.AdnCinefiloService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

/**
 * "¿Alguna recomendación?" — cruza el top-3 de género del espíritu
 * cinéfilo del usuario (AdnCinefiloService) contra los géneros de las
 * películas en cartelera (CarteleraLiveScraperService, que a su vez los
 * saca de la ficha de cada película en carteleraargentina.com.ar).
 *
 * Los nombres de género de la fuente son texto editorial libre, no
 * necesariamente calcados de nuestro propio catálogo (Genre.name,
 * alineado a TMDb en español) — confirmado un caso real: la fuente dice
 * "Familiar", nosotros usamos "Familia". El matching normaliza (sin
 * acentos, minúsculas) y aplica una tabla de equivalencias chica para
 * los casos ya confirmados u obvios; cualquier término que no matchee
 * ni por normalización ni por la tabla queda logueado para poder sumarlo
 * después, en vez de intentar adivinar todos los sinónimos de antemano.
 */
@Service
@RequiredArgsConstructor
public class RecomendacionEspirituService {

    private static final Logger log = LoggerFactory.getLogger(RecomendacionEspirituService.class);

    private final AdnCinefiloService adnCinefiloService;
    private final CarteleraLiveScraperService carteleraLiveScraperService;

    // Nuestro catálogo real (Genre.name), normalizado — para saber si un
    // término de la fuente ya es conocido tal cual, sin necesidad de
    // loguearlo como "sin reconocer".
    private static final Set<String> GENEROS_CONOCIDOS = Set.of(
            "accion", "aventura", "animacion", "comedia", "crimen", "documental",
            "drama", "familia", "fantasia", "historia", "terror", "musica",
            "misterio", "romance", "ciencia ficcion", "pelicula de tv",
            "suspense", "belica", "western"
    );

    // generoDeLaFuente (normalizado) → nuestro nombre real (normalizado).
    // Se suma acá a medida que se detectan casos nuevos — ver el log
    // "[recomendacion] género sin reconocer" para encontrarlos.
    private static final Map<String, String> EQUIVALENCIAS_GENERO = Map.of(
            "familiar", "familia",
            "infantil", "familia",
            "suspenso", "suspense",
            "policial", "crimen",
            "biografia", "historia",
            "musical", "musica"
    );

    private String normalizar(String s) {
        String sinAcentos = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase().trim();
    }

    /** Traduce un género de la fuente a nuestro nombre normalizado, logueando si no se reconoce. */
    private String traducir(String generoFuente) {
        String norm = normalizar(generoFuente);
        if (EQUIVALENCIAS_GENERO.containsKey(norm)) return EQUIVALENCIAS_GENERO.get(norm);
        if (!GENEROS_CONOCIDOS.contains(norm)) {
            log.info("[recomendacion] género sin reconocer: '{}'", generoFuente);
        }
        return norm;
    }

    public RecomendacionEspirituDto recomendar(Long userId) {
        List<GenreScoreDto> adn = adnCinefiloService.calcular(userId);
        List<GenreScoreDto> top3 = adn.stream().limit(3).toList();

        if (top3.isEmpty()) {
            // Sin ADN calculado todavía (nunca votó, o todo lo votado dio
            // negativo) — no hay con qué matchear nada, así que se corta
            // acá con un CTA claro en vez de forzar una recomendación
            // vacía de sentido.
            String cta = "Hasta el momento no nos contaste qué películas o series te gustan o no te gustaron. "
                    + "Comenzá a votar, comentar y recomendar sobre tus pelis o series favoritas, aquellas que no te "
                    + "gustaron tanto, y descubrí tu espíritu cinéfilo. De esta manera podremos darte las mejores "
                    + "recomendaciones de todas.";
            return new RecomendacionEspirituDto(List.of(), false, List.of(), cta);
        }

        List<CarteleraLiveScraperService.PeliculaEnCartelera> cartelera =
                carteleraLiveScraperService.obtenerPeliculasEnCartelera();

        List<Candidata> candidatas = new ArrayList<>();
        for (var p : cartelera) {
            if (p.genero() == null || p.genero().isEmpty()) continue;

            Set<String> generosPeliTraducidos = new HashSet<>();
            for (String g : p.genero()) generosPeliTraducidos.add(traducir(g));

            List<GenreScoreDto> matches = top3.stream()
                    .filter(g -> generosPeliTraducidos.contains(normalizar(g.genero())))
                    .toList();
            if (matches.isEmpty()) continue;

            // El puntaje de ranking es la SUMA de los porcentajes de los
            // géneros que matchean — no la cantidad de géneros. Así, una
            // película de 1 solo género MUY representativo del usuario
            // puede ganarle a una de 2 géneros poco relevantes, y
            // viceversa. No se obliga a cubrir los 3 géneros del espíritu
            // cinéfilo ni a "una recomendación por género" — se muestran,
            // sin más, las 3 que mejor lo reflejan con lo que hay
            // disponible hoy en cartelera.
            double score = matches.stream().mapToDouble(GenreScoreDto::porcentaje).sum();
            candidatas.add(new Candidata(p, matches, score));
        }

        candidatas.sort(Comparator.comparingDouble(Candidata::score).reversed());
        List<Candidata> top = candidatas.stream().limit(3).toList();

        List<RecomendacionItemDto> resultado;
        if (!top.isEmpty()) {
            resultado = new ArrayList<>();
            for (int i = 0; i < top.size(); i++) {
                resultado.add(construirItem(top.get(i).pelicula(), top.get(i).matches(), i));
            }
        } else if (!cartelera.isEmpty()) {
            // Sin ninguna coincidencia en toda la cartelera: se recomienda
            // igual la primera disponible, con un mensaje honesto que no
            // finge afinidad — mejor que dejar la pantalla vacía.
            var p = cartelera.get(0);
            String nombresTop3 = top3.stream().map(GenreScoreDto::genero).reduce((a, b) -> a + ", " + b).orElse("");
            String mensaje = "Esta semana no encontramos nada que calce con tu espíritu cinéfilo (" + nombresTop3
                    + ") — igualmente te dejamos esta candidata, por si te tienta salir un poco de lo tuyo.";
            resultado = List.of(new RecomendacionItemDto(p.slug(), p.titulo(), p.poster(), p.genero(), 0, mensaje));
        } else {
            resultado = List.of();
        }

        return new RecomendacionEspirituDto(top3, !top.isEmpty(), resultado, null);
    }

    private record Candidata(CarteleraLiveScraperService.PeliculaEnCartelera pelicula, List<GenreScoreDto> matches, double score) {}

    // 3 variantes para el caso de 1 sola coincidencia — mismo dato (género
    // + %), redacción distinta según la posición en pantalla, para que no
    // se lean calcadas cuando las 3 recomendaciones caen en este mismo
    // nivel (esperable si los 3 géneros del espíritu cinéfilo del usuario
    // no suelen combinarse en una misma película, ej. Terror + Animación).
    // A propósito, ninguna insinúa "peor" ni "última opción" — no hay
    // jerarquía real de calidad entre estar en el puesto 1, 2 o 3 acá.
    private static final List<String> MENSAJES_UN_MATCH = List.of(
            "Esta conecta directo con tu lado %s — el %s%% de tu espíritu cinéfilo que se inclina para ese lado. Una forma distinta de vivir tu tótem en pantalla grande.",
            "Tu espíritu cinéfilo tiene una veta %s bien marcada (%s%%) — y esta película la representa como pocas.",
            "Acá se despierta tu lado %s (%s%%) — otra cara de lo que sos como cinéfilo."
    );

    private RecomendacionItemDto construirItem(CarteleraLiveScraperService.PeliculaEnCartelera p, List<GenreScoreDto> matches, int posicion) {
        String mensaje = switch (matches.size()) {
            case 3 -> String.format(
                    "Tu espíritu cinéfilo tiene un perfil bien marcado: %s (%s%%), %s (%s%%) y %s (%s%%) son los tres pilares que te definen como cinéfilo. "
                            + "Esta película combina exactamente esos tres mundos: un calce prácticamente perfecto.",
                    matches.get(0).genero(), formatearPorcentaje(matches.get(0).porcentaje()),
                    matches.get(1).genero(), formatearPorcentaje(matches.get(1).porcentaje()),
                    matches.get(2).genero(), formatearPorcentaje(matches.get(2).porcentaje()));
            case 2 -> String.format(
                    "Esta película toca dos de los pilares de tu espíritu cinéfilo — %s y %s — así que tiene todo para engancharte, "
                            + "aunque no sea un calce perfecto tiene todo para convertirse en una de las favoritas.",
                    matches.get(0).genero(), matches.get(1).genero());
            default -> String.format(
                    MENSAJES_UN_MATCH.get(posicion % MENSAJES_UN_MATCH.size()),
                    matches.get(0).genero(), formatearPorcentaje(matches.get(0).porcentaje()));
        };
        return new RecomendacionItemDto(p.slug(), p.titulo(), p.poster(), p.genero(), matches.size(), mensaje);
    }

    /** "35.0" → "35" — el % ya viene con 1 decimal desde AdnCinefiloService; se saca el ".0" cuando es entero. */
    private String formatearPorcentaje(double porcentaje) {
        if (porcentaje == Math.floor(porcentaje)) return String.valueOf((int) porcentaje);
        return String.valueOf(porcentaje);
    }
}
