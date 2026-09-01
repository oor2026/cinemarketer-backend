package com.example.demo.application.services;

import com.example.demo.application.dtos.MovieFilterDto;
import com.example.demo.application.dtos.external.tmdb.*;
import com.example.demo.infrastructure.external.tmdb.TmdbService;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MovieService {

    private final TmdbService tmdbService;
    private final String imageBaseUrl;

    public MovieService(
            TmdbService tmdbService,
            @Value("${tmdb.image.base.url}") String imageBaseUrl) {
        this.tmdbService = tmdbService;
        this.imageBaseUrl = imageBaseUrl;
    }

    /**
     * Obtener películas populares (página 1 por defecto)
     */
    public TmdbPageResponseDto getPopularMovies(Integer page) {
        if (page == null || page < 1) {
            page = 1;
        }
        return tmdbService.getPopularMovies(page);
    }

    /**
     * Obtener películas en cartelera
     */
    public TmdbPageResponseDto getNowPlayingMovies(Integer page) {
        if (page == null || page < 1) {
            page = 1;
        }
        return tmdbService.getNowPlayingMovies(page);
    }

    /**
     * Obtener próximos estrenos
     */
    public TmdbPageResponseDto getUpcomingMovies(Integer page) {
        if (page == null || page < 1) {
            page = 1;
        }
        return tmdbService.getUpcomingMovies(page);
    }

    /**
     * Obtener detalles de una película por ID
     */
    public TmdbMovieDto getMovieDetails(Long movieId) {
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException("ID de película inválido");
        }
        return tmdbService.getMovieDetails(movieId);
    }

    /**
     * Buscar películas con filtros avanzados (Versión legacy - mantener por compatibilidad)
     */
    public TmdbPageResponseDto searchMovies(
            String query,
            Integer year,
            String withGenres,
            String language,
            Double voteAverageGte,
            Integer runtimeLte,
            Integer page) {

        // Construir mapa de parámetros
        Map<String, String> params = new HashMap<>();

        if (query != null && !query.trim().isEmpty()) {
            params.put("query", query.trim());
        }

        if (year != null) {
            params.put("primary_release_year", year.toString());
        }

        if (withGenres != null && !withGenres.trim().isEmpty()) {
            params.put("with_genres", withGenres);
        }

        if (language != null && !language.trim().isEmpty()) {
            params.put("with_original_language", language);
        }

        if (voteAverageGte != null) {
            params.put("vote_average.gte", voteAverageGte.toString());
        }

        if (runtimeLte != null) {
            params.put("with_runtime.lte", runtimeLte.toString());
        }

        params.put("page", page != null ? page.toString() : "1");

        return tmdbService.searchMovies(params);
    }

    /**
     * NUEVO: Buscar películas usando MovieFilterDto
     * Decide automáticamente entre /search/movie y /discover/movie
     */
    public TmdbPageResponseDto searchMovies(MovieFilterDto filter) {
        Map<String, String> params = filter.toParams();

        if (filter.usarSearch()) {
            return tmdbService.searchMovies(params);
        }

        // Si hay sortBy y no hay idioma específico, hacer dos requests paralelos es+en
        if (filter.getSortBy() != null && !filter.getSortBy().isBlank()
                && (filter.getWithOriginalLanguage() == null || filter.getWithOriginalLanguage().isBlank())) {

            Map<String, String> paramsEs = new java.util.HashMap<>(params);
            paramsEs.put("with_original_language", "es");

            Map<String, String> paramsEn = new java.util.HashMap<>(params);
            paramsEn.put("with_original_language", "en");

            TmdbPageResponseDto resEs = tmdbService.discoverMovies(paramsEs);
            TmdbPageResponseDto resEn = tmdbService.discoverMovies(paramsEn);

            // Mergear resultados deduplicando por id
            List<TmdbMovieDto> merged = new java.util.ArrayList<>();
            java.util.Set<Long> ids = new java.util.HashSet<>();

            List<TmdbMovieDto> listaEs = resEs != null && resEs.getResults() != null ? resEs.getResults() : List.of();
            List<TmdbMovieDto> listaEn = resEn != null && resEn.getResults() != null ? resEn.getResults() : List.of();

            // Intercalar es y en para mantener variedad
            int max = Math.max(listaEs.size(), listaEn.size());
            for (int i = 0; i < max; i++) {
                if (i < listaEs.size()) {
                    TmdbMovieDto p = listaEs.get(i);
                    if (p.getId() != null && ids.add(p.getId())) merged.add(p);
                }
                if (i < listaEn.size()) {
                    TmdbMovieDto p = listaEn.get(i);
                    if (p.getId() != null && ids.add(p.getId())) merged.add(p);
                }
            }

            // Ordenar merged por: año desc → popularity desc → vote_count desc
            merged.sort((a, b) -> {
                // 1. Año descendente
                int anioA = extraerAnio(a.getReleaseDate());
                int anioB = extraerAnio(b.getReleaseDate());
                int cmpAnio = Integer.compare(anioB, anioA);
                if (cmpAnio != 0) return cmpAnio;

                // 2. Popularity descendente
                double popA = a.getPopularity() != null ? a.getPopularity() : 0.0;
                double popB = b.getPopularity() != null ? b.getPopularity() : 0.0;
                int cmpPop = Double.compare(popB, popA);
                if (cmpPop != 0) return cmpPop;

                // 3. Vote count descendente
                int vcA = a.getVoteCount() != null ? a.getVoteCount() : 0;
                int vcB = b.getVoteCount() != null ? b.getVoteCount() : 0;
                return Integer.compare(vcB, vcA);
            });

            TmdbPageResponseDto result = new TmdbPageResponseDto();
            result.setPage(resEs.getPage());
            result.setResults(merged);
            result.setTotalPages(Math.max(
                    resEs.getTotalPages() != null ? resEs.getTotalPages() : 1,
                    resEn.getTotalPages() != null ? resEn.getTotalPages() : 1
            ));
            result.setTotalResults(
                    (resEs.getTotalResults() != null ? resEs.getTotalResults() : 0) +
                            (resEn.getTotalResults() != null ? resEn.getTotalResults() : 0)
            );
            return result;
        }

        return tmdbService.discoverMovies(params);
    }

    /**
     * 👇 NUEVO: Obtener lista de géneros de películas
     */
    public TmdbGenreListResponseDto getMovieGenres() {
        return tmdbService.getMovieGenres();
    }

    /**
     * Buscar personas por nombre — ordenadas por popularidad descendente
     */
    public TmdbPersonSearchResponseDto searchPeople(String query, Integer page) {
        TmdbPersonSearchResponseDto response = tmdbService.searchPeople(query, page);

        if (response != null && response.getResults() != null) {
            // Ordenar por popularidad descendente para que los famosos suban primero
            List<TmdbPersonDto> sorted = response.getResults().stream()
                    .filter(p -> p.getPopularity() != null && p.getPopularity() > 0.5)
                    .sorted(Comparator.comparingDouble(TmdbPersonDto::getPopularity).reversed())
                    .collect(Collectors.toList());

            // Si el filtro dejó muy pocos, incluir todos ordenados
            if (sorted.size() < 3) {
                sorted = response.getResults().stream()
                        .sorted(Comparator.comparingDouble(
                            p -> p.getPopularity() != null ? -p.getPopularity() : 0)
                        )
                        .collect(Collectors.toList());
            }

            response.setResults(sorted);
        }

        return response;
    }

    /**
     * Construir URL completa de una imagen (póster o backdrop)
     */
    public String getImageUrl(String path, String size) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return imageBaseUrl + "/" + size + path;
    }

    /**
     * Método helper para obtener póster en tamaño w500 (recomendado)
     */
    public String getPosterUrl(String path) {
        return getImageUrl(path, "w500");
    }

    /**
     * Método helper para obtener backdrop en tamaño original
     */
    public String getBackdropUrl(String path) {
        return getImageUrl(path, "original");
    }

    /**
     * Obtener videos (tráilers) de una película desde TMDB
     * @param movieId ID de la película en TMDB
     * @return TmdbVideoDto con la lista de videos
     */

    private int extraerAnio(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) return 0;
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public TmdbVideoDto getMovieVideos(Long movieId, String language) {
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException("ID de película inválido");
        }
        return tmdbService.getMovieVideos(movieId, language);
    }

    /**
     * Obtener películas similares por ID.
     *
     * Antes le pegaba directo a /movie/{id}/similar de TMDb — un algoritmo
     * propio de TMDb que mezcla género con señales bastante más débiles
     * (keywords sueltos, actores, década), dando resultados poco
     * consistentes (ej: Spider-Man devolvía "Con la muerte en los talones"
     * de Hitchcock, sin ningún género en común).
     *
     * Ahora arma el criterio nosotros mismos: toma los géneros reales de
     * la película y usa /discover/movie con with_genres en modo OR (pipe),
     * ordenado por popularidad — mismo mecanismo que ya usa searchMovies.
     */
    public TmdbPageResponseDto getSimilarMovies(Long movieId) {
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException("ID de película inválido");
        }

        TmdbMovieDto movie = tmdbService.getMovieDetails(movieId);

        if (movie == null) {
            return tmdbService.getSimilarMovies(movieId);
        }

        // 1) Prioridad: otras entregas de la misma saga, buscadas por nombre.
        // Se corta el título en los dos puntos ("El Señor de los Anillos:
        // La Comunidad del Anillo" → "El Señor de los Anillos") porque es
        // el patrón más común en subtítulos de franquicias. Si el título
        // no tiene ":", se busca tal cual — TMDb igual suele encontrar
        // secuelas por similitud de texto (ej: "Toy Story" → Toy Story 2, 3, 4).
        List<TmdbMovieDto> porNombre = new java.util.ArrayList<>();
        if (movie.getTitle() != null && !movie.getTitle().isBlank()) {
            String nombreBase = movie.getTitle().split(":")[0].trim();
            try {
                TmdbPageResponseDto resNombre = tmdbService.searchMovies(Map.of("query", nombreBase));
                if (resNombre != null && resNombre.getResults() != null) {
                    porNombre = resNombre.getResults().stream()
                            .filter(p -> !movieId.equals(p.getId()))
                            .collect(Collectors.toList());
                }
            } catch (Exception ignored) {}
        }

        // 2) Complemento: mismo género, para rellenar el resto de la lista.
        List<TmdbMovieDto> porGenero = new java.util.ArrayList<>();
        if (movie.getGenres() != null && !movie.getGenres().isEmpty()) {
            String generoIds = movie.getGenres().stream()
                    .map(g -> String.valueOf(g.getId()))
                    .collect(Collectors.joining("|"));

            Map<String, String> params = new HashMap<>();
            params.put("with_genres", generoIds);
            params.put("sort_by", "popularity.desc");
            params.put("page", "1");

            TmdbPageResponseDto resGenero = tmdbService.discoverMovies(params);
            if (resGenero != null && resGenero.getResults() != null) {
                porGenero = resGenero.getResults();
            }
        }

        // 3) Combinar: nombre primero, género después, sin duplicar ids
        // (ni la propia película, que ya se excluyó de "porNombre" arriba
        // pero no necesariamente de "porGenero").
        java.util.Set<Long> yaIncluidos = new java.util.HashSet<>();
        yaIncluidos.add(movieId);
        List<TmdbMovieDto> combinado = new java.util.ArrayList<>();

        for (TmdbMovieDto p : porNombre) {
            if (p.getId() != null && yaIncluidos.add(p.getId())) combinado.add(p);
        }
        for (TmdbMovieDto p : porGenero) {
            if (p.getId() != null && yaIncluidos.add(p.getId())) combinado.add(p);
        }

        if (combinado.isEmpty()) {
            return tmdbService.getSimilarMovies(movieId);
        }

        TmdbPageResponseDto resultado = new TmdbPageResponseDto();
        resultado.setPage(1);
        resultado.setResults(combinado);
        resultado.setTotalResults(combinado.size());
        resultado.setTotalPages(1);
        return resultado;
    }

    /**
     * Lista completa de plataformas disponibles (logos oficiales
     * incluidos) — no depende de ninguna película puntual.
     */
    public Object getWatchProvidersList() {
        return tmdbService.getWatchProvidersList();
    }

    /**
     * Obtener proveedores de streaming por ID de película
     */
    public Object getWatchProviders(Long movieId) {
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException("ID de película inválido");
        }
        return tmdbService.getWatchProviders(movieId);
    }

    public Object getMovieCredits(Long movieId) {
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException("ID de película inválido");
        }
        return tmdbService.getMovieCredits(movieId);
    }

    public Object getPersonDetails(Long personId) {
        if (personId == null || personId <= 0) throw new IllegalArgumentException("ID inválido");
        return tmdbService.getPersonDetails(personId);
    }

    public Object getPersonMovieCredits(Long personId) {
        if (personId == null || personId <= 0) throw new IllegalArgumentException("ID inválido");
        return tmdbService.getPersonMovieCredits(personId);
    }
}