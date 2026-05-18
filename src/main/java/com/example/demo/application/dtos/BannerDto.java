package com.example.demo.application.dtos;

import com.example.demo.domain.banner.BannerModulo;
import com.example.demo.domain.banner.BannerPosicion;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BannerDto {
    private Long id;
    private String imageUrl;
    private String linkDestino;
    private BannerModulo modulo;
    private BannerPosicion posicion;
    private Boolean visible;
    private String nombreMarca;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}