package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class CreatePublicationRequest {
    private String title;
    private Long movieId;
    private String territoryGroup;
    private String territorySub;
    private String tone;
    private String content;
    private boolean spoiler = false;
    private String[] imageUrls;
    private String videoUrl;
    private String videoUid; // UID de Cloudflare Stream — distinto de videoUrl, que es la URL final de reproducción
    private String[] hashtags;
    private boolean movieFichaEnabled = false; // Creator Tools: mostrar ficha rica en vez del link simple a la película
    private boolean countdownEnabled = false;
    private String countdownCountryCode;
    private boolean votacionEnabled = false;
    private java.util.List<OpcionVotacionRequest> opciones;
    private Integer votacionDuracionMinutos;
    private boolean rankingEnabled = false;
    private String rankingFormato;
    private String rankingModoTexto;
    private java.util.List<RankingItemRequest> rankingItems;
}