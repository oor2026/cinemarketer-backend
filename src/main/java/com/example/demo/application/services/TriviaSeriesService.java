package com.example.demo.application.services;

import com.example.demo.application.dtos.TriviaPreguntaSeriesDto;
import com.example.demo.application.dtos.external.tmdb.TmdbSeriesDto;
import com.example.demo.domain.trivia.TriviaSeriesPreguntaVistaRepository;
import com.example.demo.domain.trivia.TriviaTipoPreguntaSeries;
import com.example.demo.domain.user.User;
import com.example.demo.infrastructure.external.tmdb.TvService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TriviaSeriesService {

    private final TvService tvService;
    private final TriviaSeriesPreguntaVistaRepository vistaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final java.util.regex.Pattern SOLO_LATINOS = java.util.regex.Pattern.compile(
            "^[a-zA-ZÀ-ÿ0-9\\s\\-:,.!?'\"()\\u00C0-\\u024F\\u1E00-\\u1EFF]+$");

    public TriviaSeriesService(TvService tvService, TriviaSeriesPreguntaVistaRepository vistaRepository) {
        this.tvService = tvService;
        this.vistaRepository = vistaRepository;
    }

    public List<TriviaPreguntaSeriesDto> generarPreguntasDelDia(User user) {
        List<Long> excluidasPersonas = vistaRepository.findEntidadIdsExcluidasSinTemporada(
                user.getId(), TriviaTipoPreguntaSeries.QUIEN_ES, user.getTriviaSeriesRespondidasTotal());
        List<Long> excluidasSeries = vistaRepository.findEntidadIdsExcluidasSinTemporada(
                user.getId(), TriviaTipoPreguntaSeries.SERIE, user.getTriviaSeriesRespondidasTotal());
        return generar(user, excluidasPersonas, excluidasSeries);
    }

    public List<TriviaPreguntaSeriesDto> generarPreguntasDelDiaInvitado() {
        return generar(null, List.of(), List.of());
    }

    private List<TriviaPreguntaSeriesDto> generar(User user, List<Long> excluidasPersonas, List<Long> excluidasSeries) {

        List<TmdbSeriesDto> pool = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            var res = tvService.getPopularSeries(page);
            if (res != null && res.getResults() != null) pool.addAll(res.getResults());
        }
        pool.removeIf(s -> s.getName() == null || !SOLO_LATINOS.matcher(s.getName().trim()).matches());
        Collections.shuffle(pool);

        // 4 QUIEN_ES + 4 SERIE + 1 QUIEN_ES_TEMPORADA + 1 TEMPORADA_STILL, mezcladas
        List<String> tipos = new ArrayList<>();
        for (int i = 0; i < 4; i++) { tipos.add("QUIEN_ES"); tipos.add("SERIE"); }
        tipos.add("QUIEN_ES_TEMPORADA");
        tipos.add("TEMPORADA_STILL");
        Collections.shuffle(tipos);

        List<TriviaPreguntaSeriesDto> preguntas = new ArrayList<>();
        Set<Long> usadasEnTanda = new HashSet<>();
        boolean siguienteSerieMuestraPoster = true;

        for (String tipo : tipos) {
            TriviaPreguntaSeriesDto pregunta = null;
            // Iterador nuevo por tipo — así un subtipo que falla mucho (los
            // dos de temporada) no consume el pool que necesitan los otros.
            Iterator<TmdbSeriesDto> it = pool.iterator();

            while (pregunta == null && it.hasNext()) {
                TmdbSeriesDto serie = it.next();
                if (serie.getId() == null || usadasEnTanda.contains(serie.getId())) continue;

                switch (tipo) {
                    case "QUIEN_ES" -> pregunta = armarPreguntaQuienEs(serie, excluidasPersonas);
                    case "SERIE" -> {
                        if (excluidasSeries.contains(serie.getId())) continue;
                        pregunta = armarPreguntaSerie(serie, pool, siguienteSerieMuestraPoster);
                        siguienteSerieMuestraPoster = !siguienteSerieMuestraPoster;
                    }
                    case "QUIEN_ES_TEMPORADA" -> pregunta = armarPreguntaQuienEsTemporada(user, serie);
                    case "TEMPORADA_STILL" -> pregunta = armarPreguntaTemporadaStill(user, serie);
                }

                if (pregunta != null) usadasEnTanda.add(serie.getId());
            }

            if (pregunta != null) preguntas.add(pregunta);
        }

        return preguntas;
    }

    private TriviaPreguntaSeriesDto armarPreguntaQuienEs(TmdbSeriesDto serie, List<Long> excluidasPersonas) {
        try {
            Object creditsRaw = tvService.getSeriesAggregateCredits(serie.getId());
            Map<?, ?> credits = objectMapper.convertValue(creditsRaw, Map.class);
            List<?> cast = (List<?>) credits.get("cast");
            if (cast == null) return null;

            List<Map<String, Object>> candidatos = new ArrayList<>();
            for (Object o : cast) {
                Map<String, Object> actor = (Map<String, Object>) o;
                Object profilePath = actor.get("profile_path");
                Object id = actor.get("id");
                if (profilePath != null && id != null
                        && !excluidasPersonas.contains(((Number) id).longValue())) {
                    candidatos.add(actor);
                }
            }
            if (candidatos.size() < 4) return null;

            Collections.shuffle(candidatos);
            List<Map<String, Object>> elegidos = candidatos.subList(0, 4);

            int correctaIdx = new Random().nextInt(4);
            Map<String, Object> correcto = elegidos.get(correctaIdx);

            TriviaPreguntaSeriesDto pregunta = new TriviaPreguntaSeriesDto();
            pregunta.setTipo(TriviaTipoPreguntaSeries.QUIEN_ES);
            pregunta.setEntidadId(((Number) correcto.get("id")).longValue());
            pregunta.setImagenUrl(imageUrl((String) correcto.get("profile_path")));
            pregunta.setMostrarPoster(true);
            pregunta.setOpciones(elegidos.stream().map(a -> (String) a.get("name")).toList());
            pregunta.setCorrecta(correctaIdx);
            return pregunta;

        } catch (Exception e) {
            return null;
        }
    }

    private TriviaPreguntaSeriesDto armarPreguntaSerie(TmdbSeriesDto serie, List<TmdbSeriesDto> poolGeneral, boolean mostrarPoster) {
        try {
            var similares = tvService.getSimilarSeries(serie.getId());
            List<TmdbSeriesDto> distractoresPool = similares != null && similares.getResults() != null
                    ? new ArrayList<>(similares.getResults())
                    : new ArrayList<>();

            distractoresPool.removeIf(s -> s.getId() == null || s.getId().equals(serie.getId())
                    || s.getName() == null || !SOLO_LATINOS.matcher(s.getName().trim()).matches());

            if (distractoresPool.size() < 3) {
                for (TmdbSeriesDto s : poolGeneral) {
                    if (s.getId() != null && !s.getId().equals(serie.getId()) && s.getName() != null
                            && distractoresPool.stream().noneMatch(d -> d.getId().equals(s.getId()))) {
                        distractoresPool.add(s);
                    }
                    if (distractoresPool.size() >= 3) break;
                }
            }
            if (distractoresPool.size() < 3) return null;

            Collections.shuffle(distractoresPool);
            List<String> opciones = new ArrayList<>();
            opciones.add(serie.getName());
            for (int i = 0; i < 3; i++) opciones.add(distractoresPool.get(i).getName());

            int correctaIdx = new Random().nextInt(4);
            Collections.swap(opciones, 0, correctaIdx);

            // Si toca mostrar poster pero esta serie no tiene poster_path en
            // TMDb (pasa con series poco conocidas o con datos incompletos),
            // caemos a la variante sinopsis en vez de mostrar una imagen
            // vacía/rota. Mismo criterio inverso: si no hay overview,
            // forzamos poster si lo hay.
            boolean tienePoster = serie.getPosterPath() != null;
            boolean tieneSinopsis = serie.getOverview() != null && !serie.getOverview().isBlank();
            boolean usarPoster = mostrarPoster ? tienePoster : !tieneSinopsis && tienePoster;
            if (!usarPoster && !tieneSinopsis && !tienePoster) return null; // esta serie no sirve para esta pregunta

            TriviaPreguntaSeriesDto pregunta = new TriviaPreguntaSeriesDto();
            pregunta.setTipo(TriviaTipoPreguntaSeries.SERIE);
            pregunta.setEntidadId(serie.getId());
            pregunta.setMostrarPoster(usarPoster);
            if (usarPoster) {
                pregunta.setImagenUrl(imageUrl(serie.getPosterPath()));
            } else {
                pregunta.setSinopsis(serie.getOverview());
            }
            pregunta.setOpciones(opciones);
            pregunta.setCorrecta(correctaIdx);
            return pregunta;

        } catch (Exception e) {
            return null;
        }
    }

    private TriviaPreguntaSeriesDto armarPreguntaQuienEsTemporada(User user, TmdbSeriesDto serieBase) {
        try {
            var detalle = tvService.getSeriesDetails(serieBase.getId());
            if (detalle == null || detalle.getNumberOfSeasons() == null || detalle.getNumberOfSeasons() < 1) return null;

            List<Integer> temporadasDisponibles = new ArrayList<>();
            for (int n = 1; n <= detalle.getNumberOfSeasons(); n++) temporadasDisponibles.add(n);
            Collections.shuffle(temporadasDisponibles);

            for (Integer temporadaNumero : temporadasDisponibles) {
                var season = tvService.getSeasonDetails(serieBase.getId(), temporadaNumero);
                if (season == null || season.getEpisodes() == null) continue;

                List<Long> excluidas = user != null
                        ? vistaRepository.findEntidadIdsExcluidasPorTemporada(
                        user.getId(), TriviaTipoPreguntaSeries.QUIEN_ES_TEMPORADA,
                        temporadaNumero, user.getTriviaSeriesRespondidasTotal())
                        : List.of();

                Map<Long, String> candidatosPorId = new LinkedHashMap<>();
                Map<Long, String> nombrePorId = new LinkedHashMap<>();
                season.getEpisodes().forEach(ep -> {
                    if (ep.getGuestStars() == null) return;
                    ep.getGuestStars().forEach(g -> {
                        if (g.getId() != null && g.getProfilePath() != null
                                && !excluidas.contains(g.getId()) && !candidatosPorId.containsKey(g.getId())) {
                            candidatosPorId.put(g.getId(), g.getProfilePath());
                            nombrePorId.put(g.getId(), g.getName());
                        }
                    });
                });

                if (candidatosPorId.size() < 4) continue;

                List<Long> ids = new ArrayList<>(candidatosPorId.keySet());
                Collections.shuffle(ids);
                List<Long> elegidos = ids.subList(0, 4);

                int correctaIdx = new Random().nextInt(4);
                Long correctoId = elegidos.get(correctaIdx);

                TriviaPreguntaSeriesDto pregunta = new TriviaPreguntaSeriesDto();
                pregunta.setTipo(TriviaTipoPreguntaSeries.QUIEN_ES_TEMPORADA);
                pregunta.setEntidadId(correctoId);
                pregunta.setTemporadaNumero(temporadaNumero);
                pregunta.setSerieNombre(serieBase.getName());
                pregunta.setImagenUrl(imageUrl(candidatosPorId.get(correctoId)));
                pregunta.setMostrarPoster(true);
                pregunta.setOpciones(elegidos.stream().map(nombrePorId::get).toList());
                pregunta.setCorrecta(correctaIdx);
                return pregunta;
            }
            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private TriviaPreguntaSeriesDto armarPreguntaTemporadaStill(User user, TmdbSeriesDto serieBase) {
        try {
            var detalle = tvService.getSeriesDetails(serieBase.getId());
            // Necesitamos al menos 4 temporadas: la correcta + 3 distractores
            if (detalle == null || detalle.getNumberOfSeasons() == null || detalle.getNumberOfSeasons() < 4) return null;

            List<Integer> temporadasDisponibles = new ArrayList<>();
            for (int n = 1; n <= detalle.getNumberOfSeasons(); n++) temporadasDisponibles.add(n);
            Collections.shuffle(temporadasDisponibles);

            List<Long> excluidasGenerico = user != null
                    ? vistaRepository.findEntidadIdsExcluidasSinTemporada(
                    user.getId(), TriviaTipoPreguntaSeries.TEMPORADA_STILL, user.getTriviaSeriesRespondidasTotal())
                    : List.of(); // acá no aplica realmente (ver nota temporadaNumero abajo), pero se deja por consistencia

            for (Integer temporadaCorrecta : temporadasDisponibles) {
                List<Long> excluidasEstaTemporada = user != null
                        ? vistaRepository.findEntidadIdsExcluidasPorTemporada(
                        user.getId(), TriviaTipoPreguntaSeries.TEMPORADA_STILL,
                        temporadaCorrecta, user.getTriviaSeriesRespondidasTotal())
                        : List.of();
                if (excluidasEstaTemporada.contains(serieBase.getId())) continue;

                var season = tvService.getSeasonDetails(serieBase.getId(), temporadaCorrecta);
                if (season == null || season.getEpisodes() == null) continue;

                var episodioConStill = season.getEpisodes().stream()
                        .filter(ep -> ep.getStillPath() != null)
                        .findAny().orElse(null);
                if (episodioConStill == null) continue;

                List<Integer> distractores = new ArrayList<>(temporadasDisponibles);
                distractores.remove(temporadaCorrecta);
                if (distractores.size() < 3) continue;
                Collections.shuffle(distractores);
                List<Integer> opcionesNumeros = new ArrayList<>(distractores.subList(0, 3));
                int correctaIdx = new Random().nextInt(4);
                opcionesNumeros.add(correctaIdx, temporadaCorrecta);

                TriviaPreguntaSeriesDto pregunta = new TriviaPreguntaSeriesDto();
                pregunta.setTipo(TriviaTipoPreguntaSeries.TEMPORADA_STILL);
                pregunta.setEntidadId(serieBase.getId());
                pregunta.setTemporadaNumero(null); // es la respuesta — no se manda como dato del enunciado
                pregunta.setSerieNombre(serieBase.getName());
                pregunta.setImagenUrl(imageUrl(episodioConStill.getStillPath()));
                pregunta.setMostrarPoster(true);
                pregunta.setOpciones(opcionesNumeros.stream().map(n -> "Temporada " + n).toList());
                pregunta.setCorrecta(correctaIdx);
                return pregunta;
            }
            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private String imageUrl(String path) {
        if (path == null) return null;
        return "https://image.tmdb.org/t/p/w500" + path;
    }
}
