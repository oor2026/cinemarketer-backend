package com.example.demo.application.services;

import com.example.demo.application.dtos.GenreScoreDto;
import com.example.demo.domain.review.AdnCinefiloService;
import com.example.demo.domain.user.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tótem cinéfilo calculado del lado del servidor.
 *
 * ESPEJO de perfil.js (NOMBRE_TOTEM_POR_GENERO, NOMBRE_TOTEM_GENERO_SEXO,
 * EMOJI_POR_GENERO, EMOJI_GENERO_SEXO, _totemNombre y _totemEmoji): si se
 * agrega o cambia un tótem allá, hay que cambiarlo acá también.
 *
 * Mismo criterio que Mi Sala: el tótem es el género que encabeza el ADN
 * Cinéfilo de PELÍCULAS (el primero de AdnCinefiloService.calcular, que ya
 * viene ordenado de mayor a menor). Como se usa el mismo servicio que alimenta
 * Mi Sala, la credencial y el perfil siempre muestran el mismo tótem.
 *
 * Sexo: "F" usa la variante femenina; cualquier otra cosa (null, vacío, "M")
 * usa la masculina, igual que _esFemenino() en el front.
 */
@Service
public class TotemCinefiloService {

    public record Totem(String genero, String nombre, String emoji) {}

    private static final String EMOJI_POR_DEFECTO = "🎞️";

    private static final Map<String, String> EMOJI_POR_GENERO = Map.ofEntries(
            Map.entry("Acción", "💥"), Map.entry("Animación", "🎨"), Map.entry("Comedia", "😂"),
            Map.entry("Crimen", "🔪"), Map.entry("Documental", "🎥"), Map.entry("Drama", "🎭"),
            Map.entry("Historia", "📜"), Map.entry("Terror", "👻"), Map.entry("Música", "🎵"),
            Map.entry("Misterio", "🔎"), Map.entry("Ciencia ficción", "🚀"),
            Map.entry("Película de TV", "📺"), Map.entry("Suspense", "😰"), Map.entry("Bélica", "⚔️"),
            Map.entry("Sci-Fi & Fantasy", "🛸"), Map.entry("War & Politics", "🎖️"),
            Map.entry("News", "📰"), Map.entry("Reality", "🎪"), Map.entry("Talk", "🎙️")
    );

    /** { masculino, femenino } */
    private static final Map<String, String[]> EMOJI_GENERO_SEXO = Map.ofEntries(
            Map.entry("Romance", new String[]{"💘", "🌹"}),
            Map.entry("Fantasía", new String[]{"🧙", "🔮"}),
            Map.entry("Aventura", new String[]{"🗺️", "🗺️"}),
            Map.entry("Familia", new String[]{"👨‍👩‍👧", "👨‍👩‍👧"}),
            Map.entry("Western", new String[]{"🤠", "🤠"}),
            Map.entry("Action & Adventure", new String[]{"🏹", "🏹"}),
            Map.entry("Kids", new String[]{"🧸", "🧸"}),
            Map.entry("Soap", new String[]{"💔", "💔"})
    );

    private static final Map<String, String> NOMBRE_TOTEM_POR_GENERO = Map.ofEntries(
            Map.entry("Acción", "Bang"), Map.entry("Animación", "Garabato"), Map.entry("Comedia", "Risitas"),
            Map.entry("Crimen", "Fisgón"), Map.entry("Documental", "Bitácora"), Map.entry("Drama", "Lágrima"),
            Map.entry("Fantasía", "Duende"), Map.entry("Historia", "Retro"), Map.entry("Terror", "Boo"),
            Map.entry("Música", "Compás"), Map.entry("Misterio", "Enigma"), Map.entry("Ciencia ficción", "Astro"),
            Map.entry("Película de TV", "Maratón"), Map.entry("Suspense", "Escalofrío"), Map.entry("Bélica", "Trinchera"),
            Map.entry("Sci-Fi & Fantasy", "Portal"), Map.entry("War & Politics", "Debate"),
            Map.entry("News", "Flash"), Map.entry("Reality", "Chisme"), Map.entry("Talk", "Charla")
    );

    /** { masculino, femenino } */
    private static final Map<String, String[]> NOMBRE_TOTEM_GENERO_SEXO = Map.ofEntries(
            Map.entry("Aventura", new String[]{"Explorador", "Exploradora"}),
            Map.entry("Familia", new String[]{"Familiero", "Familiera"}),
            Map.entry("Romance", new String[]{"Cupido", "Venus"}),
            Map.entry("Western", new String[]{"Cowboy", "Vaquera"}),
            Map.entry("Fantasía", new String[]{"Mago", "Hechicera"}),
            Map.entry("Action & Adventure", new String[]{"Aventurón", "Aventurera"}),
            Map.entry("Kids", new String[]{"Osito", "Osita"}),
            Map.entry("Soap", new String[]{"Novelero", "Novelera"})
    );

    private final AdnCinefiloService adnCinefiloService;

    public TotemCinefiloService(AdnCinefiloService adnCinefiloService) {
        this.adnCinefiloService = adnCinefiloService;
    }

    /** Vacío si el usuario todavía no tiene ADN de películas (no votó ni recomendó nada). */
    public Optional<Totem> calcular(User user) {
        List<GenreScoreDto> adn = adnCinefiloService.calcular(user.getId());
        if (adn == null || adn.isEmpty()) return Optional.empty();
        return Optional.of(desdeGenero(adn.get(0).genero(), user.getSexo()));
    }

    public static Totem desdeGenero(String genero, String sexo) {
        int i = "F".equals(sexo) ? 1 : 0;

        String[] varianteNombre = NOMBRE_TOTEM_GENERO_SEXO.get(genero);
        String nombre = varianteNombre != null
                ? varianteNombre[i]
                : NOMBRE_TOTEM_POR_GENERO.getOrDefault(genero, genero);

        String[] varianteEmoji = EMOJI_GENERO_SEXO.get(genero);
        String emoji = varianteEmoji != null
                ? varianteEmoji[i]
                : EMOJI_POR_GENERO.getOrDefault(genero, EMOJI_POR_DEFECTO);

        return new Totem(genero, nombre, emoji);
    }
}
