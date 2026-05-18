package com.example.demo.web.controllers;

import com.example.demo.application.dtos.BannerDto;
import com.example.demo.domain.banner.Banner;
import com.example.demo.domain.banner.BannerModulo;
import com.example.demo.domain.banner.BannerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/banners")
public class BannerController {

    private final BannerRepository bannerRepository;

    public BannerController(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    /**
     * Obtener banners visibles para un módulo específico
     * GET /api/banners?modulo=MI_CUENTA
     * Usado por el dashboard para saber qué banners mostrar en cada vista
     */
    @GetMapping
    public ResponseEntity<List<BannerDto>> getBannersByModulo(
            @RequestParam(name = "modulo") String modulo) {
        try {
            BannerModulo bannerModulo = BannerModulo.valueOf(modulo.toUpperCase());
            List<BannerDto> dtos = bannerRepository.findByModuloAndVisibleTrue(bannerModulo)
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // =============================================
    // HELPERS
    // =============================================
    private BannerDto toDto(Banner b) {
        BannerDto dto = new BannerDto();
        dto.setId(b.getId());
        dto.setImageUrl(b.getImageUrl());
        dto.setLinkDestino(b.getLinkDestino());
        dto.setModulo(b.getModulo());
        dto.setPosicion(b.getPosicion());
        dto.setVisible(b.getVisible());
        dto.setNombreMarca(b.getNombreMarca());
        return dto;
    }
}