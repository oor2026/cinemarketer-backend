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
public class TriviaAttemptService {

    private static final ZoneId ZONA_AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int PUNTOS_POR_ACIERTO = 5;

    private final TriviaAttemptRepository attemptRepository;
    private final TriviaPreguntaVistaRepository vistaRepository;
    private final TriviaService triviaService;
    private final UserRepository userRepository;
    private final PointTransactionService pointTransactionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TriviaAttemptService(
            TriviaAttemptRepository attemptRepository,
            TriviaPreguntaVistaRepository vistaRepository,
            TriviaService triviaService,
            UserRepository userRepository,
            PointTransactionService pointTransactionService) {
        this.attemptRepository = attemptRepository;
        this.vistaRepository = vistaRepository;
        this.triviaService = triviaService;
        this.userRepository = userRepository;
        this.pointTransactionService = pointTransactionService;
    }

    /**
     * Trae el intento de hoy, o lo crea si no existe. Es el único punto de
     * entrada para "iniciar" — como el estado vive en el backend, entrar
     * desde cualquier dispositivo siempre devuelve exactamente lo mismo.
     */
    @Transactional
    public TriviaEstadoResponse obtenerOCrearIntentoDeHoy(User user) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaAttempt attempt = attemptRepository.findByUserIdAndFecha(user.getId(), hoy)
                .orElseGet(() -> crearIntento(user, hoy));

        return construirEstadoResponse(attempt);
    }

    @Transactional
    public TriviaEstadoResponse obtenerOCrearIntentoInvitado(String guestToken, String ip) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaAttempt attempt = attemptRepository.findByGuestTokenAndFecha(guestToken, hoy)
                .orElseGet(() -> attemptRepository.findByIpInvitadoAndFecha(ip, hoy)
                        .map(existente -> {
                            // Misma IP ya generó un intento hoy con OTRO token
                            // (por ejemplo, borró localStorage o abrió una
                            // ventana de incógnito) — reasignamos el token
                            // actual a ese intento existente en vez de crear
                            // uno nuevo, así no puede reiniciar borrando el
                            // navegador.
                            existente.setGuestToken(guestToken);
                            return attemptRepository.save(existente);
                        })
                        .orElseGet(() -> crearIntentoInvitado(guestToken, ip, hoy)));

        return construirEstadoResponse(attempt);
    }

    private TriviaAttempt crearIntentoInvitado(String guestToken, String ip, LocalDate hoy) {
        List<TriviaPreguntaDto> preguntas = triviaService.generarPreguntasDelDiaInvitado();

        TriviaAttempt attempt = new TriviaAttempt();
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
            throw new RuntimeException("Error generando la trivia del día", e);
        }
        return attemptRepository.save(attempt);
    }

    @Transactional
    public TriviaRespuestaResponse responderInvitado(String guestToken, int opcionElegida, int tiempoSegundos) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaAttempt attempt = attemptRepository.findByGuestTokenAndFecha(guestToken, hoy)
                .orElseThrow(() -> new IllegalStateException("No hay un intento de trivia iniciado hoy"));

        if (attempt.getEstado() != TriviaEstado.EN_CURSO) {
            throw new IllegalStateException("El intento de hoy ya terminó");
        }

        List<TriviaPreguntaDto> preguntas = deserializar(attempt.getPreguntasJson());
        TriviaPreguntaDto preguntaActual = preguntas.get(attempt.getPreguntaActual());
        boolean acerto = opcionElegida == preguntaActual.getCorrecta();

        TriviaRespuestaResponse response = new TriviaRespuestaResponse();
        response.setCorrecta(acerto);
        response.setRespuestaCorrectaIndex(preguntaActual.getCorrecta());

        if (acerto) {
            // No se otorgan puntos reales ni se escribe TriviaPreguntaVista
            // todavía — el invitado no tiene cuenta. Solo se guarda la
            // referencia en el propio intento, para poder reclamarla después.
            agregarAciertoInvitado(attempt, preguntaActual, tiempoSegundos);
            attempt.setPuntosGanados(attempt.getPuntosGanados() + PUNTOS_POR_ACIERTO);
            response.setPuntosGanadosEstaRespuesta(PUNTOS_POR_ACIERTO);

            int siguienteIndice = attempt.getPreguntaActual() + 1;
            attempt.setPreguntaActual(siguienteIndice);

            if (siguienteIndice >= preguntas.size()) {
                attempt.setEstado(TriviaEstado.GANADA);
                response.setSiguientePregunta(null);
            } else {
                response.setSiguientePregunta(aPublica(preguntas.get(siguienteIndice)));
            }
        } else {
            attempt.setEstado(TriviaEstado.PERDIDA);
            response.setPuntosGanadosEstaRespuesta(0);
            response.setSiguientePregunta(null);
        }

        try {
            attemptRepository.save(attempt);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw new RespuestaDuplicadaException();
        }
        response.setPuntosGanadosTotal(attempt.getPuntosGanados());
        response.setEstado(attempt.getEstado());
        response.setPreguntaActual(attempt.getPreguntaActual());
        return response;
    }

    private void agregarAciertoInvitado(TriviaAttempt attempt, TriviaPreguntaDto pregunta, int tiempoSegundos) {
        try {
            Map[] arr = objectMapper.readValue(attempt.getAciertosJson(), Map[].class);
            List<Map> aciertos = new ArrayList<>(Arrays.asList(arr));
            aciertos.add(Map.of("tipo", pregunta.getTipo().name(), "entidadId", pregunta.getEntidadId(), "tiempoSegundos", tiempoSegundos));
            attempt.setAciertosJson(objectMapper.writeValueAsString(aciertos));
        } catch (Exception e) {
            throw new RuntimeException("Error guardando acierto de invitado", e);
        }
    }

    /**
     * Se llama justo después de que el invitado se registra/loguea desde la
     * pantalla de resultado. Le "adopta" el intento anónimo: le atribuye los
     * puntos reales (recién ahora, todos juntos) y reconstruye el historial
     * de exclusión de 300 para cada pregunta que había acertado.
     */
    @Transactional
    public TriviaEstadoResponse reclamarIntentoInvitado(User user, String guestToken) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaAttempt attempt = attemptRepository.findByGuestTokenAndFecha(guestToken, hoy)
                .orElseThrow(() -> new IllegalStateException("No hay un intento de invitado para reclamar"));

        if (attempt.getUser() != null) {
            throw new IllegalStateException("Este intento ya fue reclamado");
        }
        if (attempt.getEstado() == TriviaEstado.EN_CURSO) {
            throw new IllegalStateException("El intento todavía no terminó");
        }
        // El usuario ya puede tener su propio intento de hoy si, por ejemplo,
        // ya jugó logueado antes de esto — en ese caso no pisamos nada.
        if (attemptRepository.findByUserIdAndFecha(user.getId(), hoy).isPresent()) {
            throw new IllegalStateException("Ya tenés un intento de trivia propio hoy");
        }

        attempt.setUser(user);
        attempt.setGuestToken(null);
        attemptRepository.save(attempt);

        if (attempt.getPuntosGanados() > 0) {
            pointTransactionService.registerTriviaEarned(user, attempt.getPuntosGanados());
        }

        try {
            Map[] arrAciertos = objectMapper.readValue(attempt.getAciertosJson(), Map[].class);
            for (Map acierto : arrAciertos) {
                TriviaPreguntaDto dto = new TriviaPreguntaDto();
                dto.setTipo(TriviaTipoPregunta.valueOf((String) acierto.get("tipo")));
                dto.setEntidadId(((Number) acierto.get("entidadId")).longValue());
                int tiempo = acierto.get("tiempoSegundos") != null ? ((Number) acierto.get("tiempoSegundos")).intValue() : 0;
                user.incrementarTriviaRespondidas();
                user.registrarAciertoTrivia(tiempo);
                registrarPreguntaVista(user, dto);
            }
            userRepository.save(user);
        } catch (Exception e) {
            throw new RuntimeException("Error reconstruyendo exclusiones del invitado", e);
        }

        return construirEstadoResponse(attempt);
    }

    private TriviaAttempt crearIntento(User user, LocalDate hoy) {
        List<TriviaPreguntaDto> preguntas = triviaService.generarPreguntasDelDia(user);

        TriviaAttempt attempt = new TriviaAttempt();
        attempt.setUser(user);
        attempt.setFecha(hoy);
        attempt.setPreguntaActual(0);
        attempt.setEstado(TriviaEstado.EN_CURSO);
        attempt.setPuntosGanados(0);
        try {
            attempt.setPreguntasJson(objectMapper.writeValueAsString(preguntas));
        } catch (Exception e) {
            throw new RuntimeException("Error generando la trivia del día", e);
        }
        return attemptRepository.save(attempt);
    }

    @Transactional
    public TriviaRespuestaResponse responder(User user, int opcionElegida, int tiempoSegundos) {
        LocalDate hoy = LocalDate.now(ZONA_AR);

        TriviaAttempt attempt = attemptRepository.findByUserIdAndFecha(user.getId(), hoy)
                .orElseThrow(() -> new IllegalStateException("No hay un intento de trivia iniciado hoy"));

        if (attempt.getEstado() != TriviaEstado.EN_CURSO) {
            throw new IllegalStateException("El intento de hoy ya terminó");
        }

        List<TriviaPreguntaDto> preguntas = deserializar(attempt.getPreguntasJson());
        TriviaPreguntaDto preguntaActual = preguntas.get(attempt.getPreguntaActual());

        boolean acerto = opcionElegida == preguntaActual.getCorrecta();

        // Se cuenta SIEMPRE, acierte o no — es la base de la ventana de 300
        User userFresco = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        userFresco.incrementarTriviaRespondidas();
        userRepository.save(userFresco);

        TriviaRespuestaResponse response = new TriviaRespuestaResponse();
        response.setCorrecta(acerto);
        response.setRespuestaCorrectaIndex(preguntaActual.getCorrecta());

        if (acerto) {
            registrarPreguntaVista(userFresco, preguntaActual);
            userFresco.registrarAciertoTrivia(tiempoSegundos);
            userRepository.save(userFresco);

            pointTransactionService.registerTriviaEarned(userFresco, PUNTOS_POR_ACIERTO);
            attempt.setPuntosGanados(attempt.getPuntosGanados() + PUNTOS_POR_ACIERTO);
            response.setPuntosGanadosEstaRespuesta(PUNTOS_POR_ACIERTO);

            int siguienteIndice = attempt.getPreguntaActual() + 1;
            attempt.setPreguntaActual(siguienteIndice);

            if (siguienteIndice >= preguntas.size()) {
                attempt.setEstado(TriviaEstado.GANADA);
                response.setSiguientePregunta(null);
            } else {
                response.setSiguientePregunta(aPublica(preguntas.get(siguienteIndice)));
            }
        } else {
            attempt.setEstado(TriviaEstado.PERDIDA);
            response.setPuntosGanadosEstaRespuesta(0);
            response.setSiguientePregunta(null);
        }

        try {
            attemptRepository.save(attempt);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw new RespuestaDuplicadaException();
        }

        response.setPuntosGanadosTotal(attempt.getPuntosGanados());
        response.setEstado(attempt.getEstado());
        response.setPreguntaActual(attempt.getPreguntaActual());
        return response;
    }

    private void registrarPreguntaVista(User user, TriviaPreguntaDto pregunta) {
        TriviaPreguntaVista vista = vistaRepository
                .findByUserIdAndTipoAndEntidadId(user.getId(), pregunta.getTipo(), pregunta.getEntidadId())
                .orElseGet(TriviaPreguntaVista::new);
        vista.setUser(user);
        vista.setTipo(pregunta.getTipo());
        vista.setEntidadId(pregunta.getEntidadId());
        vista.setRespondidasTotalAlAcertar(user.getTriviaRespondidasTotal());
        vistaRepository.save(vista);
    }

    private TriviaEstadoResponse construirEstadoResponse(TriviaAttempt attempt) {
        TriviaEstadoResponse response = new TriviaEstadoResponse();
        response.setEstado(attempt.getEstado());
        response.setPuntosGanados(attempt.getPuntosGanados());

        List<TriviaPreguntaDto> preguntas = deserializar(attempt.getPreguntasJson());
        response.setTotalPreguntas(preguntas.size());
        response.setPreguntaActual(attempt.getPreguntaActual() + 1); // 1-indexed para mostrar

        if (attempt.getEstado() == TriviaEstado.EN_CURSO) {
            response.setPregunta(aPublica(preguntas.get(attempt.getPreguntaActual())));
        }
        return response;
    }

    private TriviaPreguntaPublicaDto aPublica(TriviaPreguntaDto p) {
        TriviaPreguntaPublicaDto dto = new TriviaPreguntaPublicaDto();
        dto.setTipo(p.getTipo());
        dto.setImagenUrl(p.getImagenUrl());
        dto.setMostrarPoster(p.isMostrarPoster());
        dto.setSinopsis(p.getSinopsis());
        dto.setOpciones(p.getOpciones());
        return dto;
    }

    public List<TriviaRankingDto> obtenerRanking(Long userIdActual) {
        List<Object[]> filas = userRepository.findRankingTrivia(userIdActual != null ? userIdActual : -1L);
        return filas.stream().map(f -> new TriviaRankingDto(
                ((Number) f[4]).intValue(),           // posicion
                (String) f[1],                         // name
                ((Number) f[2]).intValue(),            // aciertos
                ((Number) f[3]).longValue(),           // tiempo
                userIdActual != null && userIdActual.equals(((Number) f[0]).longValue())
        )).toList();
    }

    private List<TriviaPreguntaDto> deserializar(String json) {
        try {
            TriviaPreguntaDto[] arr = objectMapper.readValue(json, TriviaPreguntaDto[].class);
            return new ArrayList<>(Arrays.asList(arr));
        } catch (Exception e) {
            throw new RuntimeException("Error leyendo la trivia guardada", e);
        }
    }
    public static class RespuestaDuplicadaException extends RuntimeException {}
}