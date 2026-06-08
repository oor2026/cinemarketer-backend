package com.example.demo.application.dtos;

import com.example.demo.domain.user.UserLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long id;
    private String token;
    private String type;
    private String email;
    private String role;
    private int totalPoints;
    private boolean success;
    private String message;
    private UserLevel level;        // nivel del usuario (AMATEUR, COLABORADOR, etc.)
    private boolean isPremium;      // si tiene suscripción premium activa
    private boolean profileComplete;
    private String googleId;

    // Constructor sin mensaje ni level (compatibilidad con código existente)
    public LoginResponse(String token, String type, String email, String role, int totalPoints, boolean success) {
        this.token = token;
        this.type = type;
        this.email = email;
        this.role = role;
        this.totalPoints = totalPoints;
        this.success = success;
        this.message = null;
        this.level = null;
        this.isPremium = false;
    }

    // Constructor con mensaje (suspensión, etc.)
    public LoginResponse(String token, String type, String email, String role, int totalPoints, boolean success, String message) {
        this(token, type, email, role, totalPoints, success);
        this.message = message;
    }
}