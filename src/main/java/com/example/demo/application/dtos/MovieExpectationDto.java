package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class MovieExpectationDto {
    private Double average;
    private Long count;
    private Integer userRating; // null si el usuario todavía no calificó
}