package com.example.demo.config;

import com.example.demo.domain.pointconfig.PointAction;
import com.example.demo.domain.point.PointConfig;
import com.example.demo.domain.point.PointConfigRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final PointConfigRepository pointConfigRepository;

    public DataInitializer(PointConfigRepository pointConfigRepository) {
        this.pointConfigRepository = pointConfigRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedPointConfig();
    }

    private void seedPointConfig() {
        // Solo inserta si no existen — idempotente, se puede reiniciar sin duplicar
        insertIfNotExists(PointAction.VOTE_MOVIE,    5,  "Puntos por votar una película");
        insertIfNotExists(PointAction.VOTE_CINEMA,   15, "Puntos por votar un cine");
        insertIfNotExists(PointAction.COMMENT_MOVIE, 10, "Puntos por comentar una película");

        System.out.println("✅ PointConfig seed completado");
    }

    private void insertIfNotExists(PointAction action, int points, String description) {
        if (pointConfigRepository.findByAction(action).isEmpty()) {
            PointConfig config = new PointConfig();
            config.setAction(action);
            config.setPoints(points);
            config.setDescription(description);
            config.setActive(true);
            pointConfigRepository.save(config);
            System.out.println("📌 PointConfig insertado: " + action + " → " + points + " pts");
        }
    }
}
