package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class RewardPublicDto {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Integer pointsRequired;
    private String tipo; // "COMUN" o "ESPECIAL"
    private boolean sorteo; // true solo para especiales de tipo SORTEO
}