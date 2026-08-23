package com.example.demo.application.services;

import com.example.demo.application.dtos.*;
import com.example.demo.domain.trivia.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class TriviaSeriesAttemptService {

    private static final ZoneId ZONA_AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int PUNTOS_POR_ACIERTO = 5;

    private final TriviaSeriesAttemptRepository attemptRepository;
    private final TriviaSeriesPreguntaVistaRepository vistaRepository;
    private final TriviaSeriesService triviaSeriesService;
    private final UserRepository userRepository;
    private final PointTransactionService pointTransactionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TriviaSeriesAttemptService(
            TriviaSeriesAttemptRepository attemptRepository,
            TriviaSeriesPreguntaVistaRepository vistaRepository,
            TriviaSeriesService triviaSeriesService,
            UserRepository userRepository,
            PointTransactionService pointTransactionService) {
        this.attemptRepository = attemptRepository;
        this.vistaRepository = vistaRepository;
        this.triviaSeriesService = triviaSeriesService;
        this.userRepository = userRepository;
        this.pointTransactionService = pointTransactionService;
    }

    @Transactional
    public TriviaEstadoSeriesResponse obtenerOCrearIntentoDeHoy(User user) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaSeriesAttempt attempt = attemptRepository.findByUserIdAndFecha(user.getId(), hoy)
                .orElseGet(() -> crearIntento(user, hoy));

        return construirEstadoResponse(attempt);
    }

    @Transactional
    public TriviaEstadoSeriesResponse obtenerOCrearIntentoInvitado(String guestToken, String ip) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaSeriesAttempt attempt = attemptRepository.findByGuestTokenAndFecha(guestToken, hoy)
                .orElseGet(() -> attemptRepository.findByIpInvitadoAndFecha(ip, hoy)
                        .map(existente -> {
                            existente.setGuestToken(guestToken);
                            return attemptRepository.save(existente);
                        })
                        .orElseGet(() -> crearIntentoInvitado(guestToken, ip, hoy)));

        return construirEstadoResponse(attempt);
    }

    private TriviaSeriesAttempt crearIntentoInvitado(String guestToken, String ip, LocalDate hoy) {
        List<TriviaPreguntaSeriesDto> preguntas = triviaSeriesService.generarPreguntasDelDiaInvitado();

        TriviaSeriesAttempt attempt = new TriviaSeriesAttempt();
        attempt.setGuestToken(guestToken);
        attempt.setIpInvitado(ip);
        attempt.setFecha(hoy);
        attempt.setPreguntaActual(0);
        attempt.setEstado(TriviaEstado.EN_CURSO);
        attempt.setPuntosGanados(0);
        try {
            attempt.setPreguntasJson(objectMapper.writeValueAsString(preguntas));
            attempt.setAciertosJson(objectMapper.writeValueAsString(List.of()));
        } catch (Exception e) {
            throw new RuntimeException("Error generando la trivia de series del día", e);
        }
        return attemptRepository.save(attempt);
    }

    @Transactional
    public TriviaRespuestaSeriesResponse responderInvitado(String guestToken, int opcionElegida, int tiempoSegundos) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaSeriesAttempt attempt = attemptRepository.findByGuestTokenAndFecha(guestToken, hoy)
                .orElseThrow(() -> new IllegalStateException("No hay un intento de trivia de series iniciado hoy"));

        if (attempt.getEstado() != TriviaEstado.EN_CURSO) {
            throw new IllegalStateException("El intento de hoy ya terminó");
        }

        List<TriviaPreguntaSeriesDto> preguntas = deserializar(attempt.getPreguntasJson());
        TriviaPreguntaSeriesDto preguntaActual = preguntas.get(attempt.getPreguntaActual());
        boolean acerto = opcionElegida == preguntaActual.getCorrecta();

        TriviaRespuestaSeriesResponse response = new TriviaRespuestaSeriesResponse();
        response.setCorrecta(acerto);
        response.setRespuestaCorrectaIndex(preguntaActual.getCorrecta());

        if (acerto) {
            agregarAciertoInvitado(attempt, preguntaActual, tiempoSegundos);
            attempt.setPuntosGanados(attempt.getPuntosGanados() + PUNTOS_POR_ACIERTO);
            response.setPuntosGanadosEstaRespuesta(PUNTOS_POR_ACIERTO);
        } else {
            response.setPuntosGanadosEstaRespuesta(0);
        }

        int siguienteIndice = attempt.getPreguntaActual() + 1;
        attempt.setPreguntaActual(siguienteIndice);

        if (siguienteIndice >= preguntas.size()) {
            boolean las10Correctas = (attempt.getPuntosGanados() / PUNTOS_POR_ACIERTO) >= preguntas.size();
            attempt.setEstado(las10Correctas ? TriviaEstado.GANADA : TriviaEstado.PERDIDA);
            response.setSiguientePregunta(null);
        } else {
            response.setSiguientePregunta(aPublica(preguntas.get(siguienteIndice)));
        }

        try {
            attemptRepository.save(attempt);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw new TriviaAttemptService.RespuestaDuplicadaException();
        }
        response.setPuntosGanadosTotal(attempt.getPuntosGanados());
        response.setAciertos(attempt.getPuntosGanados() / PUNTOS_POR_ACIERTO);
        response.setEstado(attempt.getEstado());
        response.setPreguntaActual(attempt.getPreguntaActual() + 1); // 1-indexed para mostrar, mismo criterio que construirEstadoResponse
        return response;
    }

    private void agregarAciertoInvitado(TriviaSeriesAttempt attempt, TriviaPreguntaSeriesDto pregunta, int tiempoSegundos) {
        try {
            Map[] arr = objectMapper.readValue(attempt.getAciertosJson(), Map[].class);
            List<Map> aciertos = new ArrayList<>(Arrays.asList(arr));
            Map<String, Object> acierto = new java.util.HashMap<>();
            acierto.put("tipo", pregunta.getTipo().name());
            acierto.put("entidadId", pregunta.getEntidadId());
            acierto.put("temporadaNumero", pregunta.getTemporadaNumero());
            acierto.put("tiempoSegundos", tiempoSegundos);
            aciertos.add(acierto);
            attempt.setAciertosJson(objectMapper.writeValueAsString(aciertos));
        } catch (Exception e) {
            throw new RuntimeException("Error guardando acierto de invitado", e);
        }
    }

    @Transactional
    public TriviaEstadoSeriesResponse reclamarIntentoInvitado(User user, String guestToken) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaSeriesAttempt attempt = attemptRepository.findByGuestTokenAndFecha(guestToken, hoy)
                .orElseThrow(() -> new IllegalStateException("No hay intento de invitado para reclamar"));

        if (attempt.getUser() != null) {
            throw new IllegalStateException("Este intento ya fue reclamado");
        }
        if (attemptRepository.findByUserIdAndFecha(user.getId(), hoy).isPresent()) {
            throw new IllegalStateException("Ya tenés un intento de trivia de series propio hoy");
        }

        attempt.setUser(user);
        attempt.setGuestToken(null);
        attemptRepository.save(attempt);

        if (attempt.getPuntosGanados() > 0) {
            pointTransactionService.registerTriviaSeriesEarned(user, attempt.getPuntosGanados());
        }

        try {
            Map[] arrAciertos = objectMapper.readValue(attempt.getAciertosJson(), Map[].class);
            for (Map acierto : arrAciertos) {
                TriviaPreguntaSeriesDto dto = new TriviaPreguntaSeriesDto();
                dto.setTipo(TriviaTipoPreguntaSeries.valueOf((String) acierto.get("tipo")));
                dto.setEntidadId(((Number) acierto.get("entidadId")).longValue());
                Object temp = acierto.get("temporadaNumero");
                dto.setTemporadaNumero(temp != null ? ((Number) temp).intValue() : null);
                int tiempo = acierto.get("tiempoSegundos") != null ? ((Number) acierto.get("tiempoSegundos")).intValue() : 0;
                user.incrementarTriviaSeriesRespondidas();
                user.registrarAciertoTriviaSeries(tiempo);
                registrarPreguntaVista(user, dto);
            }
            userRepository.save(user);
        } catch (Exception e) {
            throw new RuntimeException("Error reconstruyendo exclusiones del invitado", e);
        }

        return construirEstadoResponse(attempt);
    }

    private TriviaSeriesAttempt crearIntento(User user, LocalDate hoy) {
        List<TriviaPreguntaSeriesDto> preguntas = triviaSeriesService.generarPreguntasDelDia(user);

        TriviaSeriesAttempt attempt = new TriviaSeriesAttempt();
        attempt.setUser(user);
        attempt.setFecha(hoy);
        attempt.setPreguntaActual(0);
        attempt.setEstado(TriviaEstado.EN_CURSO);
        attempt.setPuntosGanados(0);
        try {
            attempt.setPreguntasJson(objectMapper.writeValueAsString(preguntas));
        } catch (Exception e) {
            throw new RuntimeException("Error generando la trivia de series del día", e);
        }
        return attemptRepository.save(attempt);
    }

    @Transactional
    public TriviaRespuestaSeriesResponse responder(User user, int opcionElegida, int tiempoSegundos) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaSeriesAttempt attempt = attemptRepository.findByUserIdAndFecha(user.getId(), hoy)
                .orElseThrow(() -> new IllegalStateException("No hay un intento de trivia de series iniciado hoy"));

        if (attempt.getEstado() != TriviaEstado.EN_CURSO) {
            throw new IllegalStateException("El intento de hoy ya terminó");
        }

        List<TriviaPreguntaSeriesDto> preguntas = deserializar(attempt.getPreguntasJson());
        TriviaPreguntaSeriesDto preguntaActual = preguntas.get(attempt.getPreguntaActual());

        boolean acerto = opcionElegida == preguntaActual.getCorrecta();

        User userFresco = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        userFresco.incrementarTriviaSeriesRespondidas();
        userRepository.save(userFresco);

        TriviaRespuestaSeriesResponse response = new TriviaRespuestaSeriesResponse();
        response.setCorrecta(acerto);
        response.setRespuestaCorrectaIndex(preguntaActual.getCorrecta());

        if (acerto) {
            registrarPreguntaVista(userFresco, preguntaActual);
            userFresco.registrarAciertoTriviaSeries(tiempoSegundos);
            userRepository.save(userFresco);

            pointTransactionService.registerTriviaSeriesEarned(userFresco, PUNTOS_POR_ACIERTO);
            attempt.setPuntosGanados(attempt.getPuntosGanados() + PUNTOS_POR_ACIERTO);
            response.setPuntosGanadosEstaRespuesta(PUNTOS_POR_ACIERTO);
        } else {
            response.setPuntosGanadosEstaRespuesta(0);
        }

        int siguienteIndice = attempt.getPreguntaActual() + 1;
        attempt.setPreguntaActual(siguienteIndice);

        if (siguienteIndice >= preguntas.size()) {
            boolean las10Correctas = (attempt.getPuntosGanados() / PUNTOS_POR_ACIERTO) >= preguntas.size();
            attempt.setEstado(las10Correctas ? TriviaEstado.GANADA : TriviaEstado.PERDIDA);
            response.setSiguientePregunta(null);
        } else {
            response.setSiguientePregunta(aPublica(preguntas.get(siguienteIndice)));
        }

        try {
            attemptRepository.save(attempt);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw new TriviaAttemptService.RespuestaDuplicadaException();
        }

        response.setPuntosGanadosTotal(attempt.getPuntosGanados());
        response.setAciertos(attempt.getPuntosGanados() / PUNTOS_POR_ACIERTO);
        response.setEstado(attempt.getEstado());
        response.setPreguntaActual(attempt.getPreguntaActual() + 1); // 1-indexed para mostrar, mismo criterio que construirEstadoResponse
        return response;
    }

    @Transactional
    public TriviaEstadoSeriesResponse abandonarIntento(User user) {
        LocalDate hoy = LocalDate.now(ZONA_AR);
        TriviaSeriesAttempt attempt = attemptRepository.findByUserIdAndFecha(user.getId(), hoy)
                .orElseThrow(() -> new IllegalStateException("No hay un intento de trivia de series iniciado hoy"));

        if (attempt.getEstado() == TriviaEstado.EN_CURSO) {
            attempt.setEstado(TriviaEstado.PERDIDA);
            attemptRepository.save(attempt);
        }
        return construirEstadoResponse(attempt);
    }

    @Transactional
    public TriviaEstadoSeriesResponse abandonarIntentoInvitado(String guestToken) {
        LocalDate hoy = LocalDate.now(ZONA_AR);
        TriviaSeriesAttempt attempt = attemptRepository.findByGuestTokenAndFecha(guestToken, hoy)
                .orElseThrow(() -> new IllegalStateException("No hay un intento de trivia de series iniciado hoy"));

        if (attempt.getEstado() == TriviaEstado.EN_CURSO) {
            attempt.setEstado(TriviaEstado.PERDIDA);
            attemptRepository.save(attempt);
        }
        return construirEstadoResponse(attempt);
    }

    private void registrarPreguntaVista(User user, TriviaPreguntaSeriesDto pregunta) {
        TriviaSeriesPreguntaVista vista = new TriviaSeriesPreguntaVista();
        vista.setUser(user);
        vista.setTipo(pregunta.getTipo());
        vista.setEntidadId(pregunta.getEntidadId());
        vista.setTemporadaNumero(pregunta.getTemporadaNumero());
        vista.setRespondidasTotalAlAcertar(user.getTriviaSeriesRespondidasTotal());
        vistaRepository.save(vista);
    }

    private TriviaEstadoSeriesResponse construirEstadoResponse(TriviaSeriesAttempt attempt) {
        TriviaEstadoSeriesResponse response = new TriviaEstadoSeriesResponse();
        response.setEstado(attempt.getEstado());
        response.setPuntosGanados(attempt.getPuntosGanados());
        response.setAciertos(attempt.getPuntosGanados() / PUNTOS_POR_ACIERTO);

        List<TriviaPreguntaSeriesDto> preguntas = deserializar(attempt.getPreguntasJson());
        response.setTotalPreguntas(preguntas.size());
        response.setPreguntaActual(attempt.getPreguntaActual() + 1);

        if (attempt.getEstado() == TriviaEstado.EN_CURSO) {
            response.setPregunta(aPublica(preguntas.get(attempt.getPreguntaActual())));
        }
        return response;
    }

    private TriviaPreguntaSeriesPublicaDto aPublica(TriviaPreguntaSeriesDto p) {
        TriviaPreguntaSeriesPublicaDto dto = new TriviaPreguntaSeriesPublicaDto();
        dto.setTipo(p.getTipo());
        dto.setTemporadaNumero(p.getTemporadaNumero());
        dto.setSerieNombre(p.getSerieNombre());
        dto.setImagenUrl(p.getImagenUrl());
        dto.setMostrarPoster(p.isMostrarPoster());
        dto.setSinopsis(p.getSinopsis());
        dto.setOpciones(p.getOpciones());
        return dto;
    }

    public List<TriviaRankingDto> obtenerRanking(Long userIdActual) {
        List<Object[]> filas = userRepository.findRankingTriviaSeries(userIdActual != null ? userIdActual : -1L);
        return filas.stream().map(f -> new TriviaRankingDto(
                ((Number) f[0]).longValue(),
                ((Number) f[4]).intValue(),
                (String) f[1],
                ((Number) f[2]).intValue(),
                ((Number) f[3]).longValue(),
                userIdActual != null && userIdActual.equals(((Number) f[0]).longValue())
        )).toList();
    }

    private List<TriviaPreguntaSeriesDto> deserializar(String json) {
        try {
            TriviaPreguntaSeriesDto[] arr = objectMapper.readValue(json, TriviaPreguntaSeriesDto[].class);
            return new ArrayList<>(Arrays.asList(arr));
        } catch (Exception e) {
            throw new RuntimeException("Error leyendo la trivia de series guardada", e);
        }
    }
}
