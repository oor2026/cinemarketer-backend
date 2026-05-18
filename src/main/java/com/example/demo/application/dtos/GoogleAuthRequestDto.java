package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class GoogleAuthRequestDto {
    private String credential; // token de Google
}