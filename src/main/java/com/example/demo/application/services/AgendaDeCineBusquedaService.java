package com.example.demo.application.services;

import com.example.demo.domain.cinema.Cinema;
import com.example.demo.domain.cinema.CinemaRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * ESPEJO de CarteleraLiveScraperService.obtenerCinesCercanos() /
 * obtenerCinesPorTexto() — a diferencia del original (que dependía de lo
 * que hubiera podido scrapear), acá Cinema.lat/lng vienen sincronizados
 * directo de agendadecine.com para los 324 cines del directorio, así que
 * el cálculo de distancia es contra datos reales ya en base — sin tocar
 * la red en cada pedido.
 */
@Service
public class AgendaDeCineBusquedaService {

    // El controller no recibe radio como parámetro (ver CarteleraController
    // /cines-cercanos), así que queda fijo acá — único lugar a tocar si
    // hace falta cambiarlo.
    private static final double RADIO_KM_DEFAULT = 50.0;

    private final CinemaRepository cinemaRepository;

    public AgendaDeCineBusquedaService(CinemaRepository cinemaRepository) {
        this.cinemaRepository = cinemaRepository;
    }

    public List<Cinema> obtenerCinesCercanos(double lat, double lng) {
        return cinemaRepository.findByActiveTrue().stream()
                .filter(c -> c.getLat() != null && c.getLng() != null)
                .filter(c -> distanciaKm(lat, lng, c.getLat(), c.getLng()) <= RADIO_KM_DEFAULT)
                .sorted(Comparator.comparingDouble(c -> distanciaKm(lat, lng, c.getLat(), c.getLng())))
                .toList();
    }

    // Mismo criterio que el original: sin texto, devuelve todo sin filtrar
    // (el controller cae acá cuando tampoco vinieron lat/lng).
    public List<Cinema> obtenerCinesPorTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return cinemaRepository.findByActiveTrue();
        }
        String q = texto.toLowerCase().trim();
        return cinemaRepository.findByActiveTrue().stream()
                .filter(c ->
                        c.getName().toLowerCase().contains(q) ||
                                c.getCity().toLowerCase().contains(q) ||
                                c.getAddress().toLowerCase().contains(q)
                )
                .toList();
    }

    private double distanciaKm(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}