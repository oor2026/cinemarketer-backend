package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class RecommendationRequest {
    private Long movieId;
    private Long receiverId;
    private String contextType;
}
