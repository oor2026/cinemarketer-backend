package com.example.demo.infrastructure.scheduler;

import com.example.demo.domain.gusto.GustoHistorial;
import com.example.demo.domain.gusto.GustoHistorialRepository;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.domain.series.Series;
import com.example.demo.domain.series.SeriesRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job diario que detecta cambios en "Mis gustos" (favorita, última vista en
 * cine, no me canso de ver, no la banco — Películas y Series) y graba una
 * fila en el historial SOLO cuando el valor es distinto al último guardado.
 * A diferencia del Espíritu, acá no hay "composición" que fluctúe sola —
 * el usuario tiene que elegir activamente un contenido nuevo para que algo
 * cambie, así que la comparación es directa (id distinto = fila nueva).
 *
 * Corre todos los días a las 05:15 UTC = 02:15 Argentina (GMT-3) — 15
 * minutos después de EspirituSnapshotScheduler, para no competir por
 * recursos en el mismo instante.
 */
@Component
public class GustoHistorialScheduler {

    private static final Logger log = LoggerFactory.getLogger(GustoHistorialScheduler.class);

    private final UserRepository userRepository;
    private final GustoHistorialRepository gustoHistorialRepository;
    private final MovieRepository movieRepository;
    private final SeriesRepository seriesRepository;

    public GustoHistorialScheduler(UserRepository userRepository,
                                   GustoHistorialRepository gustoHistorialRepository,
                                   MovieRepository movieRepository,
                                   SeriesRepository seriesRepository) {
        this.userRepository = userRepository;
        this.gustoHistorialRepository = gustoHistorialRepository;
        this.movieRepository = movieRepository;
        this.seriesRepository = seriesRepository;
    }

    @Scheduled(cron = "0 15 5 * * *", zone = "UTC")
    @Transactional
    public void capturarCambios() {
        log.info("🎬 Iniciando detección diaria de cambios en Mis Gustos...");

        List<User> activeUsers = userRepository.findByActiveTrue();
        int grabados = 0;

        for (User user : activeUsers) {
            try {
                // PELÍCULAS
                grabados += procesarCampo(user.getId(), "PELICULA", "FAVORITA", user.getPeliculaFavoritaId(), true);
                grabados += procesarCampo(user.getId(), "PELICULA", "VISTA_CINE", user.getUltimaVistaCineId(), true);
                grabados += procesarCampo(user.getId(), "PELICULA", "NO_ME_CANSO", user.getNoMeCansoDeVerId(), true);
                grabados += procesarCampo(user.getId(), "PELICULA", "NO_LA_BANCO", user.getNoLaBancoId(), true);

                // SERIES
                grabados += procesarCampo(user.getId(), "SERIE", "FAVORITA", user.getSerieFavoritaId(), false);
                grabados += procesarCampo(user.getId(), "SERIE", "VISTA_CINE", user.getUltimaMaratonId(), false);
                grabados += procesarCampo(user.getId(), "SERIE", "NO_ME_CANSO", user.getNoMeCansoDeVerSerieId(), false);
                grabados += procesarCampo(user.getId(), "SERIE", "NO_LA_BANCO", user.getNoLaBancoSerieId(), false);
            } catch (Exception e) {
                log.error("❌ Error procesando gustos de usuario {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("🎬 Detección completada. {} filas nuevas grabadas.", grabados);
    }

    private int procesarCampo(Long userId, String tipo, String campo, Integer contenidoIdRaw, boolean esPelicula) {
        if (contenidoIdRaw == null) return 0; // el usuario nunca eligió nada acá

        Long contenidoId = contenidoIdRaw.longValue();

        var ultimo = gustoHistorialRepository
                .findTopByUserIdAndTipoAndCampoOrderByFechaDetectadoDesc(userId, tipo, campo);

        boolean cambio = ultimo.isEmpty() || !contenidoId.equals(ultimo.get().getContenidoId());
        if (!cambio) return 0;

        String titulo = null;
        String poster = null;
        if (esPelicula) {
            Movie m = movieRepository.findByTmdbId(contenidoId).orElse(null);
            if (m != null) { titulo = m.getTitle(); poster = m.getPosterPath(); }
        } else {
            Series s = seriesRepository.findByTmdbId(contenidoId).orElse(null);
            if (s != null) { titulo = s.getTitle(); poster = s.getPosterPath(); }
        }

        GustoHistorial fila = new GustoHistorial();
        fila.setUserId(userId);
        fila.setTipo(tipo);
        fila.setCampo(campo);
        fila.setContenidoId(contenidoId);
        fila.setContenidoTitulo(titulo);
        fila.setContenidoPoster(poster);
        fila.setFechaDetectado(LocalDateTime.now());
        gustoHistorialRepository.save(fila);

        return 1;
    }
}