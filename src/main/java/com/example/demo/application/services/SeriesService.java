package com.example.demo.application.services;

import com.example.demo.application.dtos.SeriesFilterDto;
import com.example.demo.application.dtos.external.tmdb.*;
import com.example.demo.infrastructure.external.tmdb.TvService;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SeriesService {

    private final TvService tvService;
    private final String imageBaseUrl;

    public SeriesService(
            TvService tvService,
            @Value("${tmdb.image.base.url}") String imageBaseUrl) {
        this.tvService = tvService;
        this.imageBaseUrl = imageBaseUrl;
    }

    public TmdbSeriesPageResponseDto getPopularSeries(Integer page) {
        if (page == null || page < 1) page = 1;
        return tvService.getPopularSeries(page);
    }

    public TmdbSeriesPageResponseDto getOnTheAirSeries(Integer page) {
        if (page == null || page < 1) page = 1;
        return tvService.getOnTheAirSeries(page);
    }

    public TmdbSeriesPageResponseDto getAiringTodaySeries(Integer page) {
        if (page == null || page < 1) page = 1;
        return tvService.getAiringTodaySeries(page);
    }

    public TmdbSeriesDto getSeriesDetails(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            throw new IllegalArgumentException("ID de serie inválido");
        }
        return tvService.getSeriesDetails(seriesId);
    }

    public TmdbSeasonDetailDto getSeasonDetails(Long seriesId, Integer seasonNumber) {
        if (seriesId == null || seriesId <= 0) {
            throw new IllegalArgumentException("ID de serie inválido");
        }
        if (seasonNumber == null || seasonNumber < 0) {
            throw new IllegalArgumentException("Número de temporada inválido");
        }
        return tvService.getSeasonDetails(seriesId, seasonNumber);
    }

    public TmdbSeriesPageResponseDto searchSeries(SeriesFilterDto filter) {
        Map<String, String> params = filter.toParams();

        if (filter.usarSearch()) {
            return tvService.searchSeries(params);
        }

        if (filter.getSortBy() != null && !filter.getSortBy().isBlank()
                && (filter.getWithOriginalLanguage() == null || filter.getWithOriginalLanguage().isBlank())) {

            Map<String, String> paramsEs = new java.util.HashMap<>(params);
            paramsEs.put("with_original_language", "es");

            Map<String, String> paramsEn = new java.util.HashMap<>(params);
            paramsEn.put("with_original_language", "en");

            TmdbSeriesPageResponseDto resEs = tvService.discoverSeries(paramsEs);
            TmdbSeriesPageResponseDto resEn = tvService.discoverSeries(paramsEn);

            List<TmdbSeriesDto> merged = new java.util.ArrayList<>();
            java.util.Set<Long> ids = new java.util.HashSet<>();

            List<TmdbSeriesDto> listaEs = resEs != null && resEs.getResults() != null ? resEs.getResults() : List.of();
            List<TmdbSeriesDto> listaEn = resEn != null && resEn.getResults() != null ? resEn.getResults() : List.of();

            int max = Math.max(listaEs.size(), listaEn.size());
            for (int i = 0; i < max; i++) {
                if (i < listaEs.size()) {
                    TmdbSeriesDto s = listaEs.get(i);
                    if (s.getId() != null && ids.add(s.getId())) merged.add(s);
                }
                if (i < listaEn.size()) {
                    TmdbSeriesDto s = listaEn.get(i);
                    if (s.getId() != null && ids.add(s.getId())) merged.add(s);
                }
            }

            merged.sort((a, b) -> {
                int anioA = extraerAnio(a.getFirstAirDate());
                int anioB = extraerAnio(b.getFirstAirDate());
                int cmpAnio = Integer.compare(anioB, anioA);
                if (cmpAnio != 0) return cmpAnio;

                double popA = a.getPopularity() != null ? a.getPopularity() : 0.0;
                double popB = b.getPopularity() != null ? b.getPopularity() : 0.0;
                int cmpPop = Double.compare(popB, popA);
                if (cmpPop != 0) return cmpPop;

                int vcA = a.getVoteCount() != null ? a.getVoteCount() : 0;
                int vcB = b.getVoteCount() != null ? b.getVoteCount() : 0;
                return Integer.compare(vcB, vcA);
            });

            TmdbSeriesPageResponseDto result = new TmdbSeriesPageResponseDto();
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

        return tvService.discoverSeries(params);
    }

    public TmdbGenreListResponseDto getSeriesGenres() {
        return tvService.getSeriesGenres();
    }

    public TmdbPersonSearchResponseDto searchPeople(String query, Integer page) {
        TmdbPersonSearchResponseDto response = tvService.searchPeople(query, page);

        if (response != null && response.getResults() != null) {
            List<TmdbPersonDto> sorted = response.getResults().stream()
                    .filter(p -> p.getPopularity() != null && p.getPopularity() > 0.5)
                    .sorted(Comparator.comparingDouble(TmdbPersonDto::getPopularity).reversed())
                    .collect(Collectors.toList());

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

    public String getImageUrl(String path, String size) {
        if (path == null || path.isEmpty()) return null;
        return imageBaseUrl + "/" + size + path;
    }

    public String getPosterUrl(String path) {
        return getImageUrl(path, "w500");
    }

    public String getBackdropUrl(String path) {
        return getImageUrl(path, "original");
    }

    private int extraerAnio(String firstAirDate) {
        if (firstAirDate == null || firstAirDate.length() < 4) return 0;
        try {
            return Integer.parseInt(firstAirDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public TmdbVideoDto getSeriesVideos(Long seriesId, String language) {
        if (seriesId == null || seriesId <= 0) {
            throw new IllegalArgumentException("ID de serie inválido");
        }
        return tvService.getSeriesVideos(seriesId, language);
    }

    /**
     * Series similares — mismo criterio que ya aplicamos en MovieService:
     * primero otras entregas de la misma saga por nombre (cortando el
     * título en ":" para sacar el nombre base), después se completa con
     * mismo género. Reemplaza el passthrough crudo a /tv/{id}/similar de
     * TMDb, que mezclaba género con señales mucho más débiles.
     */
    public TmdbSeriesPageResponseDto getSimilarSeries(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            throw new IllegalArgumentException("ID de serie inválido");
        }

        TmdbSeriesDto serie = tvService.getSeriesDetails(seriesId);

        if (serie == null) {
            return tvService.getSimilarSeries(seriesId);
        }

        // 1) Prioridad: otras entregas de la misma saga, por nombre.
        List<TmdbSeriesDto> porNombre = new java.util.ArrayList<>();
        if (serie.getName() != null && !serie.getName().isBlank()) {
            String nombreBase = serie.getName().split(":")[0].trim();
            try {
                TmdbSeriesPageResponseDto resNombre = tvService.searchSeries(Map.of("query", nombreBase));
                if (resNombre != null && resNombre.getResults() != null) {
                    porNombre = resNombre.getResults().stream()
                            .filter(s -> !seriesId.equals(s.getId()))
                            .collect(Collectors.toList());
                }
            } catch (Exception ignored) {}
        }

        // 2) Complemento: mismo género.
        List<TmdbSeriesDto> porGenero = new java.util.ArrayList<>();
        if (serie.getGenres() != null && !serie.getGenres().isEmpty()) {
            String generoIds = serie.getGenres().stream()
                    .map(g -> String.valueOf(g.getId()))
                    .collect(Collectors.joining("|"));

            Map<String, String> params = new HashMap<>();
            params.put("with_genres", generoIds);
            params.put("sort_by", "popularity.desc");
            params.put("page", "1");

            TmdbSeriesPageResponseDto resGenero = tvService.discoverSeries(params);
            if (resGenero != null && resGenero.getResults() != null) {
                porGenero = resGenero.getResults();
            }
        }

        // 3) Combinar sin duplicar ids.
        java.util.Set<Long> yaIncluidos = new java.util.HashSet<>();
        yaIncluidos.add(seriesId);
        List<TmdbSeriesDto> combinado = new java.util.ArrayList<>();

        for (TmdbSeriesDto s : porNombre) {
            if (s.getId() != null && yaIncluidos.add(s.getId())) combinado.add(s);
        }
        for (TmdbSeriesDto s : porGenero) {
            if (s.getId() != null && yaIncluidos.add(s.getId())) combinado.add(s);
        }

        if (combinado.isEmpty()) {
            return tvService.getSimilarSeries(seriesId);
        }

        TmdbSeriesPageResponseDto resultado = new TmdbSeriesPageResponseDto();
        resultado.setPage(1);
        resultado.setResults(combinado);
        resultado.setTotalResults(combinado.size());
        resultado.setTotalPages(1);
        return resultado;
    }

    public Object getWatchProviders(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            throw new IllegalArgumentException("ID de serie inválido");
        }
        return tvService.getWatchProviders(seriesId);
    }

    public Object getSeriesCredits(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            throw new IllegalArgumentException("ID de serie inválido");
        }
        return tvService.getSeriesCredits(seriesId);
    }

    public Object getContentRatings(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            throw new IllegalArgumentException("ID de serie inválido");
        }
        return tvService.getContentRatings(seriesId);
    }

    public Object getPersonDetails(Long personId) {
        if (personId == null || personId <= 0) throw new IllegalArgumentException("ID inválido");
        return tvService.getPersonDetails(personId);
    }

    public Object getPersonSeriesCredits(Long personId) {
        if (personId == null || personId <= 0) throw new IllegalArgumentException("ID inválido");
        return tvService.getPersonSeriesCredits(personId);
    }
}