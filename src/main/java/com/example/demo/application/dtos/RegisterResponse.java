package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegisterResponse {

    // Getters y Setters
    private String message;
    private String email;
    private boolean emailSent;

    public RegisterResponse(String message, String email, boolean emailSent) {
        this.message = message;
        this.email = email;
        this.emailSent = emailSent;
    }

}