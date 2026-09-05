package com.example.demo.infrastructure.external.tmdb;

import com.example.demo.application.dtos.external.tmdb.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

import org.springframework.cache.annotation.Cacheable;

@Service
public class TmdbService {

    private final RestTemplate restTemplate;
    private final String apiToken;
    private final String baseUrl;

    public TmdbService(
            RestTemplate restTemplate,
            @Value("${tmdb.api.token}") String apiToken,
            @Value("${tmdb.api.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiToken = apiToken;
        this.baseUrl = baseUrl;
    }

    /**
     * Construye los headers con el token de autenticación
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);

        return headers;
    }

    /**
     * Construye la URL completa con los parámetros de consulta
     */
    private String buildUrl(String path, String... queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        builder.queryParam("language", "es-ES");

        if (queryParams.length > 0) {
            for (int i = 0; i < queryParams.length; i += 2) {
                builder.queryParam(queryParams[i], queryParams[i + 1]);
            }
        }

        return builder.build().toUriString();
    }

    /**
     * Obtener películas populares
     */
    @Cacheable(value = "tmdbListadosMovies", key = "'popular-' + #page")
    public TmdbPageResponseDto getPopularMovies(Integer page) {
        String path = "/movie/popular";
        String url = buildUrl(path, "page", page != null ? page.toString() : "1");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbPageResponseDto.class);
        return response.getBody();
    }

    /**
     * Obtener películas en cartelera
     */
    @Cacheable(value = "tmdbListadosMovies", key = "'now_playing-' + #page")
    public TmdbPageResponseDto getNowPlayingMovies(Integer page) {
        String path = "/movie/now_playing";
        String url = buildUrl(path, "page", page != null ? page.toString() : "1");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbPageResponseDto.class);
        return response.getBody();
    }

    /**
     * Obtener próximos estrenos
     */
    @Cacheable(value = "tmdbListadosMovies", key = "'upcoming-' + #page")
    public TmdbPageResponseDto getUpcomingMovies(Integer page) {
        String path = "/movie/upcoming";
        String url = buildUrl(path, "page", page != null ? page.toString() : "1");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbPageResponseDto.class);
        return response.getBody();
    }

    /**
     * Obtener detalles de una película por ID
     */
    @Cacheable(value = "tmdbMovieDetails", key = "#movieId")
    public TmdbMovieDto getMovieDetails(Long movieId) {
        String path = "/movie/" + movieId;
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbMovieDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbMovieDto.class);
        return response.getBody();
    }

    /**
     * Buscar películas por título (SOLO búsqueda por texto)
     * Usa el endpoint /search/movie
     */
    public TmdbPageResponseDto searchMovies(String query, Integer page) {
        String path = "/search/movie";  // ✅ CORREGIDO: antes era /discover/movie
        String url = buildUrl(path,
                "query", query,
                "page", page != null ? page.toString() : "1",
                "include_adult", "false"
        );
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbPageResponseDto.class);
        return response.getBody();
    }

    /**
     * Buscar películas con filtros (versión mapa - usa /search/movie)
     */
    public TmdbPageResponseDto searchMovies(Map<String, String> params) {
        String path = "/search/movie";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        builder.queryParam("language", "es-ES");
        builder.queryParam("include_adult", "false");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        String url = builder.build().toUriString();
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbPageResponseDto.class);
        return response.getBody();
    }

    /**
     * NUEVO: Descubrir películas con filtros avanzados (endpoint /discover/movie)
     */
    public TmdbPageResponseDto discoverMovies(Map<String, String> params) {
        String path = "/discover/movie";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);

        // Parámetros por defecto para discover
        builder.queryParam("language", "es-ES");
        builder.queryParam("include_adult", "false");
        builder.queryParam("include_video", "false");

        // Agregar todos los parámetros del mapa
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }

        // sort_by por defecto solo si el mapa no trae uno propio
        if (!params.containsKey("sort_by")) {
            builder.queryParam("sort_by", "popularity.desc");
        }

        String url = builder.build().toUriString();

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<TmdbPageResponseDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                TmdbPageResponseDto.class
        );

        return response.getBody();
    }

    /**
     * Obtener lista de géneros de películas
     */
    @Cacheable(value = "tmdbGenresMovies")
    public TmdbGenreListResponseDto getMovieGenres() {
        String path = "/genre/movie/list";
        String url = buildUrl(path);

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<TmdbGenreListResponseDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                TmdbGenreListResponseDto.class
        );

        return response.getBody();
    }

    /**
     * Buscar personas por nombre (actores, directores, etc.)
     */
    public TmdbPersonSearchResponseDto searchPeople(String query, Integer page) {
        String path = "/search/person";
        String url = buildUrl(path,
                "query", query,
                "page", page != null ? page.toString() : "1",
                "include_adult", "false"
        );

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<TmdbPersonSearchResponseDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                TmdbPersonSearchResponseDto.class
        );

        return response.getBody();
    }

    /**
     * Obtener videos (tráilers, teasers, etc.) de una película por ID
     * @param movieId ID de la película en TMDB
     * @return TmdbVideoDto con la lista de videos
     */

    @Cacheable(value = "tmdbVideosMovies", key = "#movieId + '-' + #language")
    public TmdbVideoDto getMovieVideos(Long movieId, String language) {
        String path = "/movie/" + movieId + "/videos";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        builder.queryParam("language", language != null ? language : "es-MX");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbVideoDto> response = restTemplate.exchange(
                builder.build().toUriString(), HttpMethod.GET, entity, TmdbVideoDto.class);
        return response.getBody();
    }

    /**
     * Obtener películas similares por ID
     */
    @Cacheable(value = "tmdbSimilarMovies", key = "#movieId")
    public TmdbPageResponseDto getSimilarMovies(Long movieId) {
        String path = "/movie/" + movieId + "/similar";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbPageResponseDto.class);
        return response.getBody();
    }

    /**
     * Lista completa de plataformas de streaming disponibles (con sus
     * logos oficiales) — a diferencia de getWatchProviders(movieId),
     * esto NO depende de ninguna película puntual. Se usa para el
     * selector de plataformas del buscador asistido.
     */
    @Cacheable(value = "tmdbWatchProvidersListMovies")
    public Object getWatchProvidersList() {
        String path = "/watch/providers/movie";
        String url = buildUrl(path, "watch_region", "AR");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    /**
     * Obtener proveedores de streaming por ID de película
     */
    @Cacheable(value = "tmdbWatchProvidersMovies", key = "#movieId")
    public Object getWatchProviders(Long movieId) {
        String path = "/movie/" + movieId + "/watch/providers";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbCreditsMovies", key = "'credits-' + #movieId")
    public Object getMovieCredits(Long movieId) {
        String path = "/movie/" + movieId + "/credits";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    /**
     * Obtener fechas de estreno por país — usado por Creator Tools
     * (Cuenta regresiva de estreno) para validar que la fecha elegida
     * todavía no haya pasado.
     */
    @Cacheable(value = "tmdbCreditsMovies", key = "'release_dates-' + #movieId")
    public Object getReleaseDates(Long movieId) {
        String path = "/movie/" + movieId + "/release_dates";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbPersonas", key = "'details-' + #personId")
    public Object getPersonDetails(Long personId) {
        String path = "/person/" + personId;
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbPersonas", key = "'movie_credits-' + #personId")
    public Object getPersonMovieCredits(Long personId) {
        String path = "/person/" + personId + "/movie_credits";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }
}