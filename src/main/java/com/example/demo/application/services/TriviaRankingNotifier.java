package com.example.demo.application.services;

import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Avisa por la campanita (y por push) a quienes fueron superados en el ranking de Trivia de Películas o de Series.
 *
 * Cuándo: cada vez que alguien suma aciertos (al responder bien, o al reclamar el intento de un invitado), los
 * servicios de trivia llaman a programarAviso() con cómo estaba esa persona ANTES y cómo quedó DESPUÉS.
 *
 * Reglas:
 *  - Solo se avisa a quienes estaban entre los puestos 1 y 25, que son los que muestra el ranking.
 *  - "Superar" es tener más aciertos, o los mismos con menos tiempo (el orden del ranking). Un empate exacto no cuenta.
 *  - El aviso sale DESPUÉS del commit: si la respuesta se descarta (por ejemplo por un intento duplicado) no se avisa
 *    de algo que no pasó.
 *  - Se hace en un hilo aparte: el usuario que está jugando no espera el envío de los push.
 *  - Nada de lo que pase acá puede romper la respuesta de la trivia: todo error se registra y se sigue.
 *
 * El antirruido (un aviso vivo por persona, enfriamiento del push y del mismo par) está en
 * NotificationService.crearTriviaSuperado().
 */
@Service
public class TriviaRankingNotifier {

    private static final Logger log = LoggerFactory.getLogger(TriviaRankingNotifier.class);

    /** Puestos que se muestran públicamente en el ranking: solo quienes estaban ahí reciben el aviso. */
    public static final int TOP_PUBLICO = 25;

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ExecutorService ejecutor = Executors.newFixedThreadPool(2, r -> {
        Thread hilo = new Thread(r, "trivia-ranking-avisos");
        hilo.setDaemon(true);
        return hilo;
    });

    public TriviaRankingNotifier(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @PreDestroy
    public void cerrar() {
        ejecutor.shutdown();
    }

    /**
     * Se llama desde dentro de la transacción que suma los aciertos. Deja el aviso programado para cuando esa
     * transacción se confirme, y no devuelve nada ni lanza nada: no puede afectar a quien está jugando.
     */
    public void programarAviso(boolean series, Long actorId, String actorName,
                               int aciertosAntes, long tiempoAntes, int aciertosDespues, long tiempoDespues) {
        try {
            if (actorId == null || aciertosDespues <= aciertosAntes) return;   // no mejoró: no superó a nadie

            Runnable enSegundoPlano = () -> {
                try {
                    ejecutor.execute(() -> avisarSuperados(series, actorId, actorName,
                            aciertosAntes, tiempoAntes, aciertosDespues, tiempoDespues));
                } catch (Exception e) {
                    log.warn("No se pudo programar el aviso de trivia del usuario {}: {}", actorId, e.getMessage());
                }
            };

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        enSegundoPlano.run();
                    }
                });
            } else {
                enSegundoPlano.run();
            }
        } catch (Exception e) {
            log.warn("No se pudo programar el aviso de trivia del usuario {}: {}", actorId, e.getMessage());
        }
    }

    /**
     * Busca a quienes la persona superó y les crea el aviso. Público para poder probarlo: en el sistema lo llama
     * programarAviso(). Cada aviso va en su propia transacción (la de NotificationService.crearTriviaSuperado),
     * así que uno que falle no arrastra a los demás.
     */
    public void avisarSuperados(boolean series, Long actorId, String actorName,
                                int aciertosAntes, long tiempoAntes, int aciertosDespues, long tiempoDespues) {
        try {
            // tope = TOP_PUBLICO + 1: con el ranking ya actualizado, quien estaba en el puesto 25 ahora está en el 26.
            int tope = TOP_PUBLICO + 1;
            List<Object[]> filas = series
                    ? userRepository.findSuperadosTriviaSeries(actorId, aciertosDespues, tiempoDespues, aciertosAntes, tiempoAntes, tope)
                    : userRepository.findSuperadosTrivia(actorId, aciertosDespues, tiempoDespues, aciertosAntes, tiempoAntes, tope);
            if (filas == null || filas.isEmpty()) return;

            // Columnas: id, name, aciertos, tiempo, posicion (la de ahora)
            Map<Long, Long> posicionPorUsuario = new LinkedHashMap<>();
            for (Object[] fila : filas) {
                posicionPorUsuario.put(((Number) fila[0]).longValue(), ((Number) fila[4]).longValue());
            }

            Map<Long, User> receptores = new HashMap<>();
            for (User u : userRepository.findAllById(posicionPorUsuario.keySet())) {
                receptores.put(u.getId(), u);
            }

            for (Map.Entry<Long, Long> e : posicionPorUsuario.entrySet()) {
                User receptor = receptores.get(e.getKey());
                if (receptor == null) continue;
                try {
                    notificationService.crearTriviaSuperado(receptor, actorId, actorName, series, e.getValue());
                } catch (Exception ex) {
                    log.warn("No se pudo crear el aviso de trivia para el usuario {}: {}", e.getKey(), ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("No se pudieron avisar los superados de trivia (usuario {}): {}", actorId, e.getMessage());
        }
    }
}