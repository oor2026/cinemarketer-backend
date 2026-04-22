package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuesta después de asignar avatar
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvatarAssignmentResponse {
    private Long userId;
    private String userEmail;
    private String avatarName;
    private String avatarUrl;
    private String message;
}