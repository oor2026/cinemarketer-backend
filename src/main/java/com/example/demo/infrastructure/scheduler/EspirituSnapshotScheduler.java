package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.dtos.GenreScoreDto;
import com.example.demo.domain.review.AdnCinefiloService;
import com.example.demo.domain.series.AdnCinefiloSeriesService;
import com.example.demo.domain.espiritu.EspirituSnapshot;
import com.example.demo.domain.espiritu.EspirituSnapshotGenero;
import com.example.demo.domain.espiritu.EspirituSnapshotGeneroRepository;
import com.example.demo.domain.espiritu.EspirituSnapshotRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Job diario que fotografía el ADN Cinéfilo/Seriéfilo de cada usuario activo
 * — solo graba una fila nueva cuando cambia la COMPOSICIÓN de sus 3 géneros
 * más fuertes (cuáles son, o el orden entre ellos), no ante cualquier
 * variación decimal de porcentaje. Cuando dispara, guarda el detalle
 * completo (todos los géneros con puntaje, no solo el top 3).
 *
 * Corre todos los días a las 05:00 UTC = 02:00 Argentina (GMT-3).
 */
@Component
public class EspirituSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(EspirituSnapshotScheduler.class);

    // Mismo mapa que perfil.js (NOMBRE_TOTEM_POR_GENERO) — portado acá para
    // que el job pueda calcular el nombre del tótem sin depender del navegador.
    private static final Map<String, String> NOMBRE_TOTEM_POR_GENERO = Map.ofEntries(
            Map.entry("Acción", "Bang"), Map.entry("Aventura", "Explorador"),
            Map.entry("Animación", "Garabato"), Map.entry("Comedia", "Risitas"),
            Map.entry("Crimen", "Fisgón"), Map.entry("Documental", "Bitácora"),
            Map.entry("Drama", "Lágrima"), Map.entry("Familia", "Familiero"),
            Map.entry("Fantasía", "Duende"), Map.entry("Historia", "Retro"),
            Map.entry("Terror", "Boo"), Map.entry("Música", "Compás"),
            Map.entry("Misterio", "Enigma"), Map.entry("Romance", "Cupido"),
            Map.entry("Ciencia ficción", "Astro"), Map.entry("Película de TV", "Maratón"),
            Map.entry("Suspense", "Escalofrío"), Map.entry("Bélica", "Trinchera"),
            Map.entry("Western", "Cowboy"), Map.entry("Action & Adventure", "Aventurón"),
            Map.entry("Sci-Fi & Fantasy", "Portal"), Map.entry("War & Politics", "Debate"),
            Map.entry("Soap", "Culebrón"), Map.entry("Kids", "Osito"),
            Map.entry("News", "Flash"), Map.entry("Reality", "Chisme"),
            Map.entry("Talk", "Charla")
    );

    private final UserRepository userRepository;
    private final AdnCinefiloService adnCinefiloService;
    private final AdnCinefiloSeriesService adnCinefiloSeriesService;
    private final EspirituSnapshotRepository snapshotRepository;
    private final EspirituSnapshotGeneroRepository snapshotGeneroRepository;

    public EspirituSnapshotScheduler(UserRepository userRepository,
                                     AdnCinefiloService adnCinefiloService,
                                     AdnCinefiloSeriesService adnCinefiloSeriesService,
                                     EspirituSnapshotRepository snapshotRepository,
                                     EspirituSnapshotGeneroRepository snapshotGeneroRepository) {
        this.userRepository = userRepository;
        this.adnCinefiloService = adnCinefiloService;
        this.adnCinefiloSeriesService = adnCinefiloSeriesService;
        this.snapshotRepository = snapshotRepository;
        this.snapshotGeneroRepository = snapshotGeneroRepository;
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "UTC")
    @Transactional
    public void capturarSnapshots() {
        log.info("🧬 Iniciando snapshot diario de Espíritu Cinéfilo/Seriéfilo...");

        List<User> activeUsers = userRepository.findByActiveTrue();
        int procesados = 0;
        int grabados = 0;

        for (User user : activeUsers) {
            try {
                if (procesarTipo(user.getId(), "PELICULA", adnCinefiloService.calcular(user.getId()))) {
                    grabados++;
                }
                if (procesarTipo(user.getId(), "SERIE", adnCinefiloSeriesService.calcular(user.getId()))) {
                    grabados++;
                }
                procesados++;
            } catch (Exception e) {
                log.error("❌ Error procesando espíritu de usuario {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("🧬 Snapshot completado. {} usuarios procesados, {} fotos nuevas grabadas.", procesados, grabados);
    }

    private boolean procesarTipo(Long userId, String tipo, List<GenreScoreDto> datos) {
        if (datos == null || datos.isEmpty()) return false; // todavía sin ADN calculable

        List<String> top3Actual = datos.stream()
                .limit(3)
                .map(GenreScoreDto::genero)
                .toList();

        var ultimo = snapshotRepository.findTopByUserIdAndTipoOrderByFechaSnapshotDesc(userId, tipo);

        boolean cambio;
        if (ultimo.isEmpty()) {
            cambio = true; // primera vez que se mide a este usuario
        } else {
            List<String> top3Anterior = snapshotGeneroRepository
                    .findBySnapshot_IdOrderByPorcentajeDesc(ultimo.get().getId())
                    .stream().limit(3).map(EspirituSnapshotGenero::getGenero).toList();
            cambio = !top3Actual.equals(top3Anterior); // compara también el ORDEN, no solo el conjunto
        }

        if (!cambio) return false;

        GenreScoreDto principal = datos.get(0);
        String totemNombre = NOMBRE_TOTEM_POR_GENERO.getOrDefault(principal.genero(), principal.genero());

        EspirituSnapshot snapshot = new EspirituSnapshot();
        snapshot.setUserId(userId);
        snapshot.setTipo(tipo);
        snapshot.setFechaSnapshot(LocalDateTime.now());
        snapshot.setTotemNombre(totemNombre);
        snapshot.setGeneroPrincipal(principal.genero());
        snapshot.setPorcentajePrincipal(principal.porcentaje());
        snapshotRepository.save(snapshot);

        // Se guarda el detalle COMPLETO, no solo el top 3 — para poder
        // reconstruir toda la composición más adelante si hace falta.
        for (GenreScoreDto g : datos) {
            EspirituSnapshotGenero detalle = new EspirituSnapshotGenero();
            detalle.setSnapshot(snapshot);
            detalle.setGenero(g.genero());
            detalle.setPuntos(g.puntos());
            detalle.setPorcentaje(g.porcentaje());
            snapshotGeneroRepository.save(detalle);
        }

        return true;
    }
}