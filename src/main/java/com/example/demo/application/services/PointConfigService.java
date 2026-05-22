package com.example.demo.application.services;

import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.point.PointConfig;
import com.example.demo.domain.point.PointConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class PointConfigService {

    private final PointConfigRepository pointConfigRepository;

    public PointConfigService(PointConfigRepository pointConfigRepository) {
        this.pointConfigRepository = pointConfigRepository;
    }

    /**
     * Devuelve los puntos configurados para una acción.
     * Si no existe la configuración, devuelve 0 como fallback seguro.
     */
    public int getPoints(PointAction action) {
        return pointConfigRepository.findByAction(action)
                .filter(PointConfig::getActive)
                .map(PointConfig::getPoints)
                .orElse(0);
    }
}
