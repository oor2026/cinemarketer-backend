package com.example.demo.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caché en memoria (Caffeine) para todo lo que le pedimos a TMDb.
 * Cada caché tiene su propia vida útil según qué tan seguido cambia
 * ese tipo de dato en la realidad — no tiene sentido tratar "géneros"
 * (prácticamente estático) igual que "populares" (se siente vivo).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        List<CaffeineCache> caches = new ArrayList<>();

        // Detalle de película/serie — el título/sinopsis/poster no
        // cambian, pero vote_average/vote_count sí se actualizan.
        caches.add(buildCache("tmdbMovieDetails", 12, TimeUnit.HOURS));
        caches.add(buildCache("tmdbSeriesDetails", 12, TimeUnit.HOURS));

        // Listados (populares, en cines, próximos estrenos) — se
        // sienten "vivos", vida útil corta.
        caches.add(buildCache("tmdbListadosMovies", 2, TimeUnit.HOURS));
        caches.add(buildCache("tmdbListadosSeries", 2, TimeUnit.HOURS));
        // "Al aire hoy" es más sensible al día calendario todavía.
        caches.add(buildCache("tmdbAiringToday", 1, TimeUnit.HOURS));

        // Similares — cambia poco día a día, pero no es tan estático
        // como el detalle en sí.
        caches.add(buildCache("tmdbSimilarMovies", 12, TimeUnit.HOURS));
        caches.add(buildCache("tmdbSimilarSeries", 12, TimeUnit.HOURS));

        // Reparto/equipo técnico/temporadas/certificación — una vez
        // publicado, prácticamente no cambia.
        caches.add(buildCache("tmdbCreditsMovies", 7, TimeUnit.DAYS));
        caches.add(buildCache("tmdbCreditsSeries", 7, TimeUnit.DAYS));

        // Personas (actores/directores) — se comparte entre película y
        // serie a propósito: el mismo actor tiene el mismo TMDb ID sin
        // importar desde cuál de los dos módulos se lo busque.
        caches.add(buildCache("tmdbPersonas", 7, TimeUnit.DAYS));

        // Dónde ver, por título puntual — las plataformas van y vienen.
        caches.add(buildCache("tmdbWatchProvidersMovies", 24, TimeUnit.HOURS));
        caches.add(buildCache("tmdbWatchProvidersSeries", 24, TimeUnit.HOURS));

        // Lista completa de plataformas — casi no cambia.
        caches.add(buildCache("tmdbWatchProvidersListMovies", 7, TimeUnit.DAYS));
        caches.add(buildCache("tmdbWatchProvidersListSeries", 7, TimeUnit.DAYS));

        // Géneros — prácticamente estático.
        caches.add(buildCache("tmdbGenresMovies", 30, TimeUnit.DAYS));
        caches.add(buildCache("tmdbGenresSeries", 30, TimeUnit.DAYS));

        // Tráilers/videos — una vez publicados, no cambian.
        caches.add(buildCache("tmdbVideosMovies", 7, TimeUnit.DAYS));
        caches.add(buildCache("tmdbVideosSeries", 7, TimeUnit.DAYS));

        manager.setCaches(caches);
        return manager;
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit unit) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(duration, unit)
                        .maximumSize(2000)
                        .build());
    }
}