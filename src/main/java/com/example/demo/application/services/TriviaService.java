package com.example.demo.application.services;

import com.example.demo.application.dtos.TriviaPreguntaDto;
import com.example.demo.application.dtos.external.tmdb.TmdbMovieDto;
import com.example.demo.domain.trivia.TriviaPreguntaVistaRepository;
import com.example.demo.domain.trivia.TriviaTipoPregunta;
import com.example.demo.domain.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TriviaService {

    private final MovieService movieService;
    private final TriviaPreguntaVistaRepository vistaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TriviaService(MovieService movieService, TriviaPreguntaVistaRepository vistaRepository) {
        this.movieService = movieService;
        this.vistaRepository = vistaRepository;
    }

    /**
     * Arma las 10 preguntas del día para un usuario: 5 ¿Quién es? + 5 Adivina
     * la película, mezcladas, sin repetir películas dentro de la misma tanda
     * ni preguntas puntuales que este usuario ya haya acertado hace menos
     * de 300 respuestas.
     */
    public List<TriviaPreguntaDto> generarPreguntasDelDia(User user) {
        List<Long> excluidasPersonas = vistaRepository.findEntidadIdsExcluidas(
                user.getId(), TriviaTipoPregunta.QUIEN_ES, user.getTriviaRespondidasTotal());
        List<Long> excluidasPeliculas = vistaRepository.findEntidadIdsExcluidas(
                user.getId(), TriviaTipoPregunta.PELICULA, user.getTriviaRespondidasTotal());
        return generar(excluidasPersonas, excluidasPeliculas);
    }

    /**
     * Invitado sin cuenta: no hay historial de exclusión posible todavía
     * (no existe identidad persistente), así que arranca siempre del pool
     * completo.
     */
    public List<TriviaPreguntaDto> generarPreguntasDelDiaInvitado() {
        return generar(List.of(), List.of());
    }

    private List<TriviaPreguntaDto> generar(List<Long> excluidasPersonas, List<Long> excluidasPeliculas) {

        // Pool base: un par de páginas de populares alcanza y sobra para 10 slots
        List<TmdbMovieDto> pool = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            var res = movieService.getPopularMovies(page);
            if (res != null && res.getResults() != null) pool.addAll(res.getResults());
        }
        Collections.shuffle(pool);

        List<String> tipos = new ArrayList<>();
        for (int i = 0; i < 5; i++) { tipos.add("QUIEN_ES"); tipos.add("PELICULA"); }
        Collections.shuffle(tipos);

        List<TriviaPreguntaDto> preguntas = new ArrayList<>();
        Set<Long> usadasEnTanda = new HashSet<>();
        boolean siguientePelicMuestraPoster = true; // alterna solo entre las PELICULA

        Iterator<TmdbMovieDto> it = pool.iterator();

        for (String tipo : tipos) {
            TriviaPreguntaDto pregunta = null;

            while (pregunta == null && it.hasNext()) {
                TmdbMovieDto movie = it.next();
                if (movie.getId() == null || usadasEnTanda.contains(movie.getId())) continue;

                if ("QUIEN_ES".equals(tipo)) {
                    if (excluidasPeliculas.contains(movie.getId())) {
                        // no excluye por esto, solo referencia — QUIEN_ES excluye por personId, se chequea abajo
                    }
                    pregunta = armarPreguntaQuienEs(movie, excluidasPersonas);
                } else {
                    if (excluidasPeliculas.contains(movie.getId())) continue;
                    pregunta = armarPreguntaPelicula(movie, pool, siguientePelicMuestraPoster);
                    siguientePelicMuestraPoster = !siguientePelicMuestraPoster;
                }

                if (pregunta != null) usadasEnTanda.add(movie.getId());
            }

            if (pregunta != null) preguntas.add(pregunta);
        }

        return preguntas;
    }

    private TriviaPreguntaDto armarPreguntaQuienEs(TmdbMovieDto movie, List<Long> excluidasPersonas) {
        try {
            Object creditsRaw = movieService.getMovieCredits(movie.getId());
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

            TriviaPreguntaDto pregunta = new TriviaPreguntaDto();
            pregunta.setTipo(TriviaTipoPregunta.QUIEN_ES);
            pregunta.setEntidadId(((Number) correcto.get("id")).longValue());
            pregunta.setImagenUrl(movieService.getImageUrl((String) correcto.get("profile_path"), "w500"));
            pregunta.setMostrarPoster(true);
            pregunta.setOpciones(elegidos.stream().map(a -> (String) a.get("name")).toList());
            pregunta.setCorrecta(correctaIdx);
            return pregunta;

        } catch (Exception e) {
            return null; // esta película no sirvió, se prueba con la siguiente del pool
        }
    }

    private TriviaPreguntaDto armarPreguntaPelicula(TmdbMovieDto movie, List<TmdbMovieDto> poolGeneral, boolean mostrarPoster) {
        try {
            var similares = movieService.getSimilarMovies(movie.getId());
            List<TmdbMovieDto> distractoresPool = similares != null && similares.getResults() != null
                    ? new ArrayList<>(similares.getResults())
                    : new ArrayList<>();

            distractoresPool.removeIf(p -> p.getId() == null || p.getId().equals(movie.getId())
                    || p.getTitle() == null);

            // Si similar no trajo suficientes, completamos con el pool general
            if (distractoresPool.size() < 3) {
                for (TmdbMovieDto p : poolGeneral) {
                    if (p.getId() != null && !p.getId().equals(movie.getId()) && p.getTitle() != null
                            && distractoresPool.stream().noneMatch(d -> d.getId().equals(p.getId()))) {
                        distractoresPool.add(p);
                    }
                    if (distractoresPool.size() >= 3) break;
                }
            }
            if (distractoresPool.size() < 3) return null;

            Collections.shuffle(distractoresPool);
            List<String> opciones = new ArrayList<>();
            opciones.add(movie.getTitle());
            for (int i = 0; i < 3; i++) opciones.add(distractoresPool.get(i).getTitle());

            int correctaIdx = new Random().nextInt(4);
            Collections.swap(opciones, 0, correctaIdx);

            TriviaPreguntaDto pregunta = new TriviaPreguntaDto();
            pregunta.setTipo(TriviaTipoPregunta.PELICULA);
            pregunta.setEntidadId(movie.getId());
            pregunta.setMostrarPoster(mostrarPoster);
            if (mostrarPoster) {
                pregunta.setImagenUrl(movieService.getImageUrl(movie.getPosterPath(), "w500"));
            } else {
                pregunta.setSinopsis(movie.getOverview());
            }
            pregunta.setOpciones(opciones);
            pregunta.setCorrecta(correctaIdx);
            return pregunta;

        } catch (Exception e) {
            return null;
        }
    }
}