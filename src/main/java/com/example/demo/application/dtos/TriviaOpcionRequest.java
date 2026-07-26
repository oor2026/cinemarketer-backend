package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class TriviaOpcionRequest {
    private String texto;
    private Boolean esCorrecta;
}