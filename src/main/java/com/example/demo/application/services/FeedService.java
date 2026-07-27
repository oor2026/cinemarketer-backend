package com.example.demo.application.services;

import com.example.demo.application.dtos.FeedCarruselItemDto;
import com.example.demo.application.dtos.FeedDestacadoDto;
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
public class FeedService {

    private static final Long SINGLETON_ID = 1L;

    private final FeedDestacadoRepository feedDestacadoRepository;
    private final FeedCarruselItemRepository feedCarruselItemRepository;
    private final MovieService movieService;
    private final UserRepository userRepository;
    private final com.example.demo.domain.reward.RewardRepository rewardRepository;
    private final com.example.demo.domain.premium.PremiumRewardRepository premiumRewardRepository;

    public FeedService(FeedDestacadoRepository feedDestacadoRepository,
                       FeedCarruselItemRepository feedCarruselItemRepository,
                       MovieService movieService,
                       UserRepository userRepository,
                       com.example.demo.domain.reward.RewardRepository rewardRepository,
                       com.example.demo.domain.premium.PremiumRewardRepository premiumRewardRepository) {
        this.feedDestacadoRepository = feedDestacadoRepository;
        this.feedCarruselItemRepository = feedCarruselItemRepository;
        this.movieService = movieService;
        this.userRepository = userRepository;
        this.rewardRepository = rewardRepository;
        this.premiumRewardRepository = premiumRewardRepository;
    }

    /** Usado por el endpoint público — solo el movieId, sin datos de auditoría */
    public Long getDestacadaMovieId() {
        return feedDestacadoRepository.findById(SINGLETON_ID)
                .map(FeedDestacado::getMovieId)
                .orElse(null);
    }

    /** Usado por el admin — incluye quién/cuándo la actualizó */
    public FeedDestacadoDto getDestacadaAdmin() {
        return feedDestacadoRepository.findById(SINGLETON_ID)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public FeedDestacadoDto setDestacada(Long movieId, String adminEmail) {
        if (movieId == null) {
            throw new IllegalArgumentException("movieId es requerido");
        }

        // Validar que la película existe realmente en TMDb antes de guardar
        try {
            movieService.getMovieDetails(movieId);
        } catch (Exception e) {
            throw new IllegalArgumentException("La película no existe en TMDb (id " + movieId + ")");
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        FeedDestacado entidad = feedDestacadoRepository.findById(SINGLETON_ID)
                .orElseGet(FeedDestacado::new);
        entidad.setId(SINGLETON_ID);
        entidad.setMovieId(movieId);
        entidad.setUpdatedByAdminId(admin.getId());
        entidad.setUpdatedByAdminEmail(admin.getEmail());
        entidad.setUpdatedAt(LocalDateTime.now());
        feedDestacadoRepository.save(entidad);

        return toDto(entidad);
    }

    @Transactional
    public void quitarDestacada() {
        feedDestacadoRepository.deleteById(SINGLETON_ID);
    }

    private FeedDestacadoDto toDto(FeedDestacado f) {
        FeedDestacadoDto dto = new FeedDestacadoDto();
        dto.setMovieId(f.getMovieId());
        dto.setUpdatedAt(f.getUpdatedAt());
        dto.setUpdatedByAdminEmail(f.getUpdatedByAdminEmail());
        return dto;
    }

    // ==============================================
    // CARRUSEL DEL FEED (hasta 5 ítems: destacada + premios)
    // ==============================================
    private static final int CARRUSEL_MAX_ITEMS = 5;

    public List<FeedCarruselItemDto> getCarruselAdmin() {
        return feedCarruselItemRepository.findAllByOrderByOrderIndexAsc()
                .stream().map(this::toCarruselDto).collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void agregarPeliculaAlCarrusel(String adminEmail) {
        if (feedCarruselItemRepository.existsByTipo(FeedCarruselTipo.PELICULA_DESTACADA)) {
            throw new IllegalArgumentException("La película destacada ya está en el carrusel");
        }
        if (feedDestacadoRepository.findById(1L).isEmpty()) {
            throw new IllegalArgumentException("Primero configurá una película destacada más arriba");
        }
        crearItem(FeedCarruselTipo.PELICULA_DESTACADA, null, adminEmail);
    }

    @Transactional
    public void agregarPeliculaCarruselAlCarrusel(Long movieId, String adminEmail) {
        if (movieId == null) {
            throw new IllegalArgumentException("movieId es requerido");
        }
        try {
            movieService.getMovieDetails(movieId);
        } catch (Exception e) {
            throw new IllegalArgumentException("La película no existe en TMDb (id " + movieId + ")");
        }
        if (feedCarruselItemRepository.existsByTipoAndMovieId(FeedCarruselTipo.PELICULA_CARRUSEL, movieId)) {
            throw new IllegalArgumentException("Esa película ya está en el carrusel");
        }

        long actuales = feedCarruselItemRepository.count();
        if (actuales >= CARRUSEL_MAX_ITEMS) {
            throw new IllegalArgumentException("El carrusel ya tiene el máximo de " + CARRUSEL_MAX_ITEMS + " elementos");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        FeedCarruselItem item = new FeedCarruselItem();
        item.setTipo(FeedCarruselTipo.PELICULA_CARRUSEL);
        item.setMovieId(movieId);
        item.setOrderIndex((int) actuales);
        item.setUpdatedByAdminId(admin.getId());
        item.setUpdatedByAdminEmail(admin.getEmail());
        item.setAddedAt(LocalDateTime.now());
        feedCarruselItemRepository.save(item);
    }

    @Transactional
    public void agregarPremioAlCarrusel(FeedCarruselTipo tipo, Long rewardId, String adminEmail) {
        if (tipo != FeedCarruselTipo.PREMIO_COMUN && tipo != FeedCarruselTipo.PREMIO_ESPECIAL) {
            throw new IllegalArgumentException("Tipo inválido para un premio");
        }
        if (tipo == FeedCarruselTipo.PREMIO_COMUN) {
            rewardRepository.findById(rewardId)
                    .orElseThrow(() -> new IllegalArgumentException("El premio no existe"));
        } else {
            premiumRewardRepository.findById(rewardId)
                    .orElseThrow(() -> new IllegalArgumentException("El premio premium no existe"));
        }
        if (feedCarruselItemRepository.existsByTipoAndRewardId(tipo, rewardId)) {
            throw new IllegalArgumentException("Ese premio ya está en el carrusel");
        }
        crearItem(tipo, rewardId, adminEmail);
    }

    private void crearItem(FeedCarruselTipo tipo, Long rewardId, String adminEmail) {
        long actuales = feedCarruselItemRepository.count();
        if (actuales >= CARRUSEL_MAX_ITEMS) {
            throw new IllegalArgumentException("El carrusel ya tiene el máximo de " + CARRUSEL_MAX_ITEMS + " elementos");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));

        FeedCarruselItem item = new FeedCarruselItem();
        item.setTipo(tipo);
        item.setRewardId(rewardId);
        item.setOrderIndex((int) actuales);
        item.setUpdatedByAdminId(admin.getId());
        item.setUpdatedByAdminEmail(admin.getEmail());
        item.setAddedAt(LocalDateTime.now());
        feedCarruselItemRepository.save(item);
    }

    @Transactional
    public void quitarDelCarrusel(Long itemId) {
        feedCarruselItemRepository.deleteById(itemId);
        reindexarCarrusel();
    }

    @Transactional
    public void moverItemCarrusel(Long itemId, int direccion) {
        List<FeedCarruselItem> items = feedCarruselItemRepository.findAllByOrderByOrderIndexAsc();
        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(itemId)) { idx = i; break; }
        }
        if (idx < 0) throw new IllegalArgumentException("Ítem no encontrado");

        int nuevoIdx = idx + direccion;
        if (nuevoIdx < 0 || nuevoIdx >= items.size()) return; // ya está en el extremo

        FeedCarruselItem a = items.get(idx);
        FeedCarruselItem b = items.get(nuevoIdx);
        int tmp = a.getOrderIndex();
        a.setOrderIndex(b.getOrderIndex());
        b.setOrderIndex(tmp);
        feedCarruselItemRepository.save(a);
        feedCarruselItemRepository.save(b);
    }

    private void reindexarCarrusel() {
        List<FeedCarruselItem> items = feedCarruselItemRepository.findAllByOrderByOrderIndexAsc();
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setOrderIndex(i);
        }
        feedCarruselItemRepository.saveAll(items);
    }

    /** Público — lo que consume el feed real. Salta la película si la destacada fue borrada. */
    public List<Map<String, Object>> getCarruselPublico() {
        List<FeedCarruselItem> items = feedCarruselItemRepository.findAllByOrderByOrderIndexAsc();
        List<Map<String, Object>> resultado = new java.util.ArrayList<>();

        for (FeedCarruselItem item : items) {
            Map<String, Object> m = new HashMap<>();
            m.put("tipo", item.getTipo().name());

            if (item.getTipo() == FeedCarruselTipo.PELICULA_DESTACADA) {
                Long movieId = getDestacadaMovieId();
                if (movieId == null) continue; // se sacó la destacada pero el item quedó huérfano
                m.put("movieId", movieId);
            } else if (item.getTipo() == FeedCarruselTipo.PELICULA_CARRUSEL) {
                m.put("movieId", item.getMovieId());
            } else {
                m.put("rewardId", item.getRewardId());
            }
            resultado.add(m);
        }
        return resultado;
    }

    private FeedCarruselItemDto toCarruselDto(FeedCarruselItem item) {
        FeedCarruselItemDto dto = new FeedCarruselItemDto();
        dto.setId(item.getId());
        dto.setTipo(item.getTipo().name());
        dto.setRewardId(item.getRewardId());
        dto.setMovieId(item.getMovieId());
        dto.setOrderIndex(item.getOrderIndex());
        dto.setAddedAt(item.getAddedAt());
        dto.setUpdatedByAdminEmail(item.getUpdatedByAdminEmail());
        return dto;
    }
}