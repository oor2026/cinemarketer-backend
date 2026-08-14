package com.example.demo.application.services;

import com.example.demo.application.dtos.SeriesFeedCarruselItemDto;
import com.example.demo.application.dtos.SeriesFeedDestacadoDto;
import com.example.demo.domain.feed.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeriesFeedService {

    private static final Long SINGLETON_ID = 1L;
    private static final int CARRUSEL_MAX_ITEMS = 5;

    private final SeriesFeedDestacadoRepository seriesFeedDestacadoRepository;
    private final SeriesFeedCarruselItemRepository seriesFeedCarruselItemRepository;
    private final SeriesService seriesService;
    private final UserRepository userRepository;
    private final com.example.demo.domain.reward.RewardRepository rewardRepository;
    private final com.example.demo.domain.premium.PremiumRewardRepository premiumRewardRepository;

    public SeriesFeedService(SeriesFeedDestacadoRepository seriesFeedDestacadoRepository,
                             SeriesFeedCarruselItemRepository seriesFeedCarruselItemRepository,
                             SeriesService seriesService,
                             UserRepository userRepository,
                             com.example.demo.domain.reward.RewardRepository rewardRepository,
                             com.example.demo.domain.premium.PremiumRewardRepository premiumRewardRepository) {
        this.seriesFeedDestacadoRepository = seriesFeedDestacadoRepository;
        this.seriesFeedCarruselItemRepository = seriesFeedCarruselItemRepository;
        this.seriesService = seriesService;
        this.userRepository = userRepository;
        this.rewardRepository = rewardRepository;
        this.premiumRewardRepository = premiumRewardRepository;
    }

    public Long getDestacadaSeriesId() {
        return seriesFeedDestacadoRepository.findById(SINGLETON_ID)
                .map(SeriesFeedDestacado::getSeriesId)
                .orElse(null);
    }

    public SeriesFeedDestacadoDto getDestacadaAdmin() {
        return seriesFeedDestacadoRepository.findById(SINGLETON_ID)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public SeriesFeedDestacadoDto setDestacada(Long seriesId, String adminEmail) {
        if (seriesId == null) {
            throw new IllegalArgumentException("seriesId es requerido");
        }

        try {
            seriesService.getSeriesDetails(seriesId);
        } catch (Exception e) {
            throw new IllegalArgumentException("La serie no existe en TMDb (id " + seriesId + ")");
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        SeriesFeedDestacado entidad = seriesFeedDestacadoRepository.findById(SINGLETON_ID)
                .orElseGet(SeriesFeedDestacado::new);
        entidad.setId(SINGLETON_ID);
        entidad.setSeriesId(seriesId);
        entidad.setUpdatedByAdminId(admin.getId());
        entidad.setUpdatedByAdminEmail(admin.getEmail());
        entidad.setUpdatedAt(LocalDateTime.now());
        seriesFeedDestacadoRepository.save(entidad);

        return toDto(entidad);
    }

    @Transactional
    public void quitarDestacada() {
        seriesFeedDestacadoRepository.deleteById(SINGLETON_ID);
    }

    private SeriesFeedDestacadoDto toDto(SeriesFeedDestacado f) {
        SeriesFeedDestacadoDto dto = new SeriesFeedDestacadoDto();
        dto.setSeriesId(f.getSeriesId());
        dto.setUpdatedAt(f.getUpdatedAt());
        dto.setUpdatedByAdminEmail(f.getUpdatedByAdminEmail());
        return dto;
    }

    // ==============================================
    // CARRUSEL DEL FEED DE SERIES (hasta 5 ítems: destacada + premios)
    // ==============================================

    public List<SeriesFeedCarruselItemDto> getCarruselAdmin() {
        return seriesFeedCarruselItemRepository.findAllByOrderByOrderIndexAsc()
                .stream().map(this::toCarruselDto).collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void agregarSerieAlCarrusel(String adminEmail) {
        if (seriesFeedCarruselItemRepository.existsByTipo(SeriesFeedCarruselTipo.SERIE_DESTACADA)) {
            throw new IllegalArgumentException("La serie destacada ya está en el carrusel");
        }
        if (seriesFeedDestacadoRepository.findById(1L).isEmpty()) {
            throw new IllegalArgumentException("Primero configurá una serie destacada más arriba");
        }
        crearItem(SeriesFeedCarruselTipo.SERIE_DESTACADA, null, adminEmail);
    }

    @Transactional
    public void agregarRankingAlCarrusel(String adminEmail) {
        if (seriesFeedCarruselItemRepository.existsByTipo(SeriesFeedCarruselTipo.RANKING_TRIVIA)) {
            throw new IllegalArgumentException("El ranking de trivia ya está en el carrusel");
        }
        crearItem(SeriesFeedCarruselTipo.RANKING_TRIVIA, null, adminEmail);
    }

    @Transactional
    public void agregarSerieCarruselAlCarrusel(Long seriesId, String adminEmail) {
        if (seriesId == null) {
            throw new IllegalArgumentException("seriesId es requerido");
        }
        try {
            seriesService.getSeriesDetails(seriesId);
        } catch (Exception e) {
            throw new IllegalArgumentException("La serie no existe en TMDb (id " + seriesId + ")");
        }
        if (seriesFeedCarruselItemRepository.existsByTipoAndSeriesId(SeriesFeedCarruselTipo.SERIE_CARRUSEL, seriesId)) {
            throw new IllegalArgumentException("Esa serie ya está en el carrusel");
        }

        long actuales = seriesFeedCarruselItemRepository.count();
        if (actuales >= CARRUSEL_MAX_ITEMS) {
            throw new IllegalArgumentException("El carrusel ya tiene el máximo de " + CARRUSEL_MAX_ITEMS + " elementos");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        SeriesFeedCarruselItem item = new SeriesFeedCarruselItem();
        item.setTipo(SeriesFeedCarruselTipo.SERIE_CARRUSEL);
        item.setSeriesId(seriesId);
        item.setOrderIndex((int) actuales);
        item.setUpdatedByAdminId(admin.getId());
        item.setUpdatedByAdminEmail(admin.getEmail());
        item.setAddedAt(LocalDateTime.now());
        seriesFeedCarruselItemRepository.save(item);
    }

    @Transactional
    public void agregarPremioAlCarrusel(SeriesFeedCarruselTipo tipo, Long rewardId, String adminEmail) {
        if (tipo != SeriesFeedCarruselTipo.PREMIO_COMUN && tipo != SeriesFeedCarruselTipo.PREMIO_ESPECIAL) {
            throw new IllegalArgumentException("Tipo inválido para un premio");
        }
        if (tipo == SeriesFeedCarruselTipo.PREMIO_COMUN) {
            rewardRepository.findById(rewardId)
                    .orElseThrow(() -> new IllegalArgumentException("El premio no existe"));
        } else {
            premiumRewardRepository.findById(rewardId)
                    .orElseThrow(() -> new IllegalArgumentException("El premio premium no existe"));
        }
        if (seriesFeedCarruselItemRepository.existsByTipoAndRewardId(tipo, rewardId)) {
            throw new IllegalArgumentException("Ese premio ya está en el carrusel");
        }
        crearItem(tipo, rewardId, adminEmail);
    }

    private void crearItem(SeriesFeedCarruselTipo tipo, Long rewardId, String adminEmail) {
        long actuales = seriesFeedCarruselItemRepository.count();
        if (actuales >= CARRUSEL_MAX_ITEMS) {
            throw new IllegalArgumentException("El carrusel ya tiene el máximo de " + CARRUSEL_MAX_ITEMS + " elementos");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        SeriesFeedCarruselItem item = new SeriesFeedCarruselItem();
        item.setTipo(tipo);
        item.setRewardId(rewardId);
        item.setOrderIndex((int) actuales);
        item.setUpdatedByAdminId(admin.getId());
        item.setUpdatedByAdminEmail(admin.getEmail());
        item.setAddedAt(LocalDateTime.now());
        seriesFeedCarruselItemRepository.save(item);
    }

    @Transactional
    public void quitarDelCarrusel(Long itemId) {
        seriesFeedCarruselItemRepository.deleteById(itemId);
        reindexarCarrusel();
    }

    @Transactional
    public void moverItemCarrusel(Long itemId, int direccion) {
        List<SeriesFeedCarruselItem> items = seriesFeedCarruselItemRepository.findAllByOrderByOrderIndexAsc();
        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(itemId)) { idx = i; break; }
        }
        if (idx < 0) throw new IllegalArgumentException("Ítem no encontrado");

        int nuevoIdx = idx + direccion;
        if (nuevoIdx < 0 || nuevoIdx >= items.size()) return;

        SeriesFeedCarruselItem a = items.get(idx);
        SeriesFeedCarruselItem b = items.get(nuevoIdx);
        int tmp = a.getOrderIndex();
        a.setOrderIndex(b.getOrderIndex());
        b.setOrderIndex(tmp);
        seriesFeedCarruselItemRepository.save(a);
        seriesFeedCarruselItemRepository.save(b);
    }

    private void reindexarCarrusel() {
        List<SeriesFeedCarruselItem> items = seriesFeedCarruselItemRepository.findAllByOrderByOrderIndexAsc();
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setOrderIndex(i);
        }
        seriesFeedCarruselItemRepository.saveAll(items);
    }

    public List<Map<String, Object>> getCarruselPublico() {
        List<SeriesFeedCarruselItem> items = seriesFeedCarruselItemRepository.findAllByOrderByOrderIndexAsc();
        List<Map<String, Object>> resultado = new java.util.ArrayList<>();

        for (SeriesFeedCarruselItem item : items) {
            Map<String, Object> m = new HashMap<>();
            m.put("tipo", item.getTipo().name());

            if (item.getTipo() == SeriesFeedCarruselTipo.SERIE_DESTACADA) {
                Long seriesId = getDestacadaSeriesId();
                if (seriesId == null) continue;
                m.put("seriesId", seriesId);
            } else if (item.getTipo() == SeriesFeedCarruselTipo.SERIE_CARRUSEL) {
                m.put("seriesId", item.getSeriesId());
            } else if (item.getTipo() == SeriesFeedCarruselTipo.RANKING_TRIVIA) {
                // Igual que en Película: sin id extra, el frontend pide el
                // ranking directo a /api/trivia/ranking al abrir el modal.
            } else {
                m.put("rewardId", item.getRewardId());
            }
            resultado.add(m);
        }
        return resultado;
    }

    private SeriesFeedCarruselItemDto toCarruselDto(SeriesFeedCarruselItem item) {
        SeriesFeedCarruselItemDto dto = new SeriesFeedCarruselItemDto();
        dto.setId(item.getId());
        dto.setTipo(item.getTipo().name());
        dto.setRewardId(item.getRewardId());
        dto.setSeriesId(item.getSeriesId());
        dto.setOrderIndex(item.getOrderIndex());
        dto.setAddedAt(item.getAddedAt());
        dto.setUpdatedByAdminEmail(item.getUpdatedByAdminEmail());
        return dto;
    }
}
