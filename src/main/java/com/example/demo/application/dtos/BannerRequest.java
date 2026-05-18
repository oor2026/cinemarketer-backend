package com.example.demo.application.dtos;

import com.example.demo.domain.banner.BannerModulo;
import com.example.demo.domain.banner.BannerPosicion;
import lombok.Data;

@Data
public class BannerRequest {
    private String linkDestino;
    private BannerModulo modulo;
    private BannerPosicion posicion;
    private Boolean visible;
    private String nombreMarca;
}