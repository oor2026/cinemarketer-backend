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
}