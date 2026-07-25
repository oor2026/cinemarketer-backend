package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class RankingItemRequest {
    private Long movieId;
    private String texto;
}