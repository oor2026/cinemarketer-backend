package com.example.demo.application.services;

import com.example.demo.infrastructure.external.tmdb.TmdbService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ¿Es argentina una película? Lo usa "Cine argentino" para dejar, de lo que pasan los
 * Espacios INCAA, solo el cine nacional.
 *
 * Criterio: Argentina figura entre sus países, coproducciones incluidas (buena parte del
 * cine nacional se hace en coproducción y el INCAA las trata como nacionales).
 *
 * Fuente, en este orden:
 *   1. Los países de origen que informe agendadecine ("originCountries"). Hoy vienen vacíos,
 *      pero si algún día los completan, se usan y no hace falta consultar a TMDb.
 *   2. La ficha de TMDb, buscada por el tmdbId exacto que trae agendadecine (sin buscar por
 *      título, así no se confunde una película con otra del mismo nombre): Argentina en
 *      "production_countries" o en "origin_country".
 * Sin ninguno de los dos datos, la película no se cuenta como argentina.
 *
 * El resultado se guarda en memoria por tmdbId: el país de una película no cambia, así que se
 * pregunta una sola vez mientras el backend esté corriendo. Si TMDb falla, NO se guarda, para
 * volver a intentar la próxima vez.
 */
@Service
public class NacionalidadPeliculaService {

    private static final String ARGENTINA = "AR";

    private final TmdbService tmdbService;
    private final Map<String, Boolean> esArgentinaPorTmdbId = new ConcurrentHashMap<>();

    public NacionalidadPeliculaService(TmdbService tmdbService) {
        this.tmdbService = tmdbService;
    }

    public boolean esArgentina(String tmdbId, List<String> paisesOrigen) {
        if (paisesOrigen != null && !paisesOrigen.isEmpty()) {
            return paisesOrigen.stream().anyMatch(NacionalidadPeliculaService::esCodigoOArgentina);
        }
        if (tmdbId == null || tmdbId.isBlank()) return false;

        Boolean guardado = esArgentinaPorTmdbId.get(tmdbId);
        if (guardado != null) return guardado;

        try {
            Object ficha = tmdbService.getMovieCountries(Long.parseLong(tmdbId.trim()));
            boolean resultado = fichaDiceArgentina(ficha);
            esArgentinaPorTmdbId.put(tmdbId, resultado);
            return resultado;
        } catch (Exception e) {
            return false;   // sin guardar: se reintenta en la próxima consulta
        }
    }

    // La ficha llega como Map (JSON de TMDb): se miran "production_countries" (lista de
    // { iso_3166_1, name }) y "origin_country" (lista de códigos).
    static boolean fichaDiceArgentina(Object ficha) {
        if (!(ficha instanceof Map<?, ?> mapa)) return false;

        Object produccion = mapa.get("production_countries");
        if (produccion instanceof Collection<?> paises) {
            for (Object pais : paises) {
                if (pais instanceof Map<?, ?> p && ARGENTINA.equals(p.get("iso_3166_1"))) return true;
            }
        }

        Object origen = mapa.get("origin_country");
        if (origen instanceof Collection<?> codigos) {
            for (Object codigo : codigos) {
                if (ARGENTINA.equals(codigo)) return true;
            }
        }
        return false;
    }

    // Acepta tanto el código ("AR") como el nombre ("Argentina"), sin importar mayúsculas.
    private static boolean esCodigoOArgentina(String pais) {
        if (pais == null) return false;
        String limpio = pais.trim();
        return limpio.equalsIgnoreCase(ARGENTINA) || limpio.equalsIgnoreCase("Argentina");
    }
}