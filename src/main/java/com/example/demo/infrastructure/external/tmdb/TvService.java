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
public class TvService {

    private final RestTemplate restTemplate;
    private final String apiToken;
    private final String baseUrl;

    public TvService(
            RestTemplate restTemplate,
            @Value("${tmdb.api.token}") String apiToken,
            @Value("${tmdb.api.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiToken = apiToken;
        this.baseUrl = baseUrl;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        return headers;
    }

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

    @Cacheable(value = "tmdbListadosSeries", key = "'popular-' + #page")
    public TmdbSeriesPageResponseDto getPopularSeries(Integer page) {
        String path = "/tv/popular";
        String url = buildUrl(path, "page", page != null ? page.toString() : "1");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbSeriesPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeriesPageResponseDto.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbListadosSeries", key = "'on_the_air-' + #page")
    public TmdbSeriesPageResponseDto getOnTheAirSeries(Integer page) {
        String path = "/tv/on_the_air";
        String url = buildUrl(path, "page", page != null ? page.toString() : "1");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbSeriesPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeriesPageResponseDto.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbAiringToday", key = "#page")
    public TmdbSeriesPageResponseDto getAiringTodaySeries(Integer page) {
        String path = "/tv/airing_today";
        String url = buildUrl(path, "page", page != null ? page.toString() : "1");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbSeriesPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeriesPageResponseDto.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbSeriesDetails", key = "#seriesId")
    public TmdbSeriesDto getSeriesDetails(Long seriesId) {
        String path = "/tv/" + seriesId;
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbSeriesDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeriesDto.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbCreditsSeries", key = "'season-' + #seriesId + '-' + #seasonNumber")
    public TmdbSeasonDetailDto getSeasonDetails(Long seriesId, Integer seasonNumber) {
        String path = "/tv/" + seriesId + "/season/" + seasonNumber;
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbSeasonDetailDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeasonDetailDto.class);
        return response.getBody();
    }

    public TmdbSeriesPageResponseDto searchSeries(String query, Integer page) {
        String path = "/search/tv";
        String url = buildUrl(path,
                "query", query,
                "page", page != null ? page.toString() : "1",
                "include_adult", "false"
        );
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbSeriesPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeriesPageResponseDto.class);
        return response.getBody();
    }

    public TmdbSeriesPageResponseDto searchSeries(Map<String, String> params) {
        String path = "/search/tv";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        builder.queryParam("language", "es-ES");
        builder.queryParam("include_adult", "false");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        String url = builder.build().toUriString();
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbSeriesPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeriesPageResponseDto.class);
        return response.getBody();
    }

    public TmdbSeriesPageResponseDto discoverSeries(Map<String, String> params) {
        String path = "/discover/tv";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);

        builder.queryParam("language", "es-ES");
        builder.queryParam("include_adult", "false");

        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }

        if (!params.containsKey("sort_by")) {
            builder.queryParam("sort_by", "popularity.desc");
        }

        String url = builder.build().toUriString();
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<TmdbSeriesPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeriesPageResponseDto.class);

        return response.getBody();
    }

    @Cacheable(value = "tmdbGenresSeries")
    public TmdbGenreListResponseDto getSeriesGenres() {
        String path = "/genre/tv/list";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbGenreListResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbGenreListResponseDto.class);
        return response.getBody();
    }

    public TmdbPersonSearchResponseDto searchPeople(String query, Integer page) {
        String path = "/search/person";
        String url = buildUrl(path,
                "query", query,
                "page", page != null ? page.toString() : "1",
                "include_adult", "false"
        );
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbPersonSearchResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbPersonSearchResponseDto.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbVideosSeries", key = "#seriesId + '-' + #language")
    public TmdbVideoDto getSeriesVideos(Long seriesId, String language) {
        String path = "/tv/" + seriesId + "/videos";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        builder.queryParam("language", language != null ? language : "es-MX");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbVideoDto> response = restTemplate.exchange(
                builder.build().toUriString(), HttpMethod.GET, entity, TmdbVideoDto.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbSimilarSeries", key = "#seriesId")
    public TmdbSeriesPageResponseDto getSimilarSeries(Long seriesId) {
        String path = "/tv/" + seriesId + "/similar";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<TmdbSeriesPageResponseDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, TmdbSeriesPageResponseDto.class);
        return response.getBody();
    }

    /**
     * Lista completa de plataformas de streaming disponibles para
     * series, con sus logos oficiales — no depende de ninguna serie
     * puntual. Gemela de TmdbService.getWatchProvidersList().
     */
    @Cacheable(value = "tmdbWatchProvidersListSeries")
    public Object getWatchProvidersList() {
        String path = "/watch/providers/tv";
        String url = buildUrl(path, "watch_region", "AR");
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbWatchProvidersSeries", key = "#seriesId")
    public Object getWatchProviders(Long seriesId) {
        String path = "/tv/" + seriesId + "/watch/providers";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbCreditsSeries", key = "'credits-' + #seriesId")
    public Object getSeriesCredits(Long seriesId) {
        String path = "/tv/" + seriesId + "/credits";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    // A diferencia de getSeriesCredits (solo trae el cast de la última
    // temporada emitida), este trae el cast agregado de TODAS las
    // temporadas — necesario para que "¿Quién es?" a nivel serie completa
    // sea representativo y no dependa de qué temporada salió última.
    @Cacheable(value = "tmdbCreditsSeries", key = "'aggregate_credits-' + #seriesId")
    public Object getSeriesAggregateCredits(Long seriesId) {
        String path = "/tv/" + seriesId + "/aggregate_credits";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }

    @Cacheable(value = "tmdbCreditsSeries", key = "'content_ratings-' + #seriesId")
    public Object getContentRatings(Long seriesId) {
        String path = "/tv/" + seriesId + "/content_ratings";
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

    @Cacheable(value = "tmdbPersonas", key = "'series_credits-' + #personId")
    public Object getPersonSeriesCredits(Long personId) {
        String path = "/person/" + personId + "/tv_credits";
        String url = buildUrl(path);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Object.class);
        return response.getBody();
    }
}