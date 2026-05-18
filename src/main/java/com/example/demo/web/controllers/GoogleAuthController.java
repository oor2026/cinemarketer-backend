package com.example.demo.web.controllers;

import com.example.demo.application.services.GoogleAuthService;
import com.example.demo.application.dtos.GoogleAuthRequestDto;
import com.example.demo.application.dtos.CompleteProfileRequestDto;
import com.example.demo.application.dtos.LoginResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;

    public GoogleAuthController(GoogleAuthService googleAuthService) {
        this.googleAuthService = googleAuthService;
    }

    /**
     * Autenticar usuario con Google
     * POST /api/auth/google
     */
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleAuth(
            @RequestBody GoogleAuthRequestDto request) {
        LoginResponse response = googleAuthService.authenticateWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Completar perfil de usuario Google (DNI + teléfono)
     * POST /api/auth/google/complete-profile
     */
    @PostMapping("/google/complete-profile")
    public ResponseEntity<LoginResponse> completeProfile(
            @RequestBody CompleteProfileRequestDto request,
            @RequestHeader("Authorization") String authHeader) {
        LoginResponse response = googleAuthService.completeProfile(request, authHeader);
        return ResponseEntity.ok(response);
    }
}