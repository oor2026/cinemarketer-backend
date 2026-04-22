package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RewardBasicDto {
    private Long id;
    private String name;
    private Integer pointsRequired;
    private String imageUrl;
    private String partner;
    private String website;
}