package com.example.demo.web.controllers;

import com.example.demo.application.dtos.*;
import com.example.demo.application.services.AvatarService;
import com.example.demo.application.services.UserService;
import com.example.demo.domain.avatar.Avatar;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador para gestionar avatares
 * Endpoints para usuarios y administradores
 */
@RestController
@RequestMapping("/api/avatars")
public class AvatarController {

    private final AvatarService avatarService;
    private final UserService userService;

    public AvatarController(AvatarService avatarService, UserService userService) {
        this.avatarService = avatarService;
        this.userService = userService;
    }

    // ==============================================
    // ENDPOINTS PÚBLICOS / PARA USUARIOS AUTENTICADOS
    // ==============================================

    /**
     * Obtiene todos los avatares disponibles para el usuario actual
     * GET /api/avatars/available
     */
    @GetMapping("/available")
    public ResponseEntity<List<AvatarDto>> getAvailableAvatars(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Avatar> avatars = avatarService.getAvatarsForUser(user.getId());

        List<AvatarDto> response = avatars.stream()
                .map(AvatarDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene avatares agrupados por nivel
     * GET /api/avatars/by-level
     */
    @GetMapping("/by-level")
    public ResponseEntity<List<AvatarsByLevelDto>> getAvatarsByLevel(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<AvatarsByLevelDto> response = new ArrayList<>();

        for (UserLevel level : UserLevel.values()) {
            List<Avatar> avatarsForLevel = avatarService.getAvatarsForUser(user.getId())
                    .stream()
                    .filter(a -> a.getRequiredLevel() == null || a.getRequiredLevel() == level)
                    .collect(Collectors.toList());

            if (!avatarsForLevel.isEmpty()) {
                AvatarsByLevelDto dto = new AvatarsByLevelDto(
                        level,
                        level.getDisplayName(),
                        avatarsForLevel.stream().map(AvatarDto::fromEntity).collect(Collectors.toList())
                );
                response.add(dto);
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene el avatar por defecto para el nivel del usuario actual
     * GET /api/avatars/default
     */
    @GetMapping("/default")
    public ResponseEntity<AvatarDto> getDefaultAvatarForCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Avatar defaultAvatar = avatarService.getDefaultAvatarForLevel(user.getLevel());

        return ResponseEntity.ok(AvatarDto.fromEntity(defaultAvatar));
    }

    /**
     * Asigna un avatar predefinido al usuario actual
     * POST /api/avatars/assign/{avatarId}
     */
    @PostMapping("/assign/{avatarId}")
    public ResponseEntity<AvatarAssignmentResponse> assignAvatar(
            @PathVariable Long avatarId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User updatedUser = avatarService.assignAvatarToUser(user.getId(), avatarId);
        Avatar assignedAvatar = avatarService.getAvatarById(avatarId);

        AvatarAssignmentResponse response = new AvatarAssignmentResponse(
                updatedUser.getId(),
                updatedUser.getEmail(),
                assignedAvatar.getName(),
                updatedUser.getAvatarUrl(),
                "Avatar asignado correctamente"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Sube y asigna un avatar personalizado
     * POST /api/avatars/upload
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<AvatarAssignmentResponse> uploadCustomAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User updatedUser = avatarService.assignCustomAvatar(user.getId(), file);

        AvatarAssignmentResponse response = new AvatarAssignmentResponse(
                updatedUser.getId(),
                updatedUser.getEmail(),
                "Avatar personalizado",
                updatedUser.getAvatarUrl(),
                "Avatar personalizado subido correctamente"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Restablece al avatar por defecto del nivel actual
     * POST /api/avatars/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<AvatarAssignmentResponse> resetToDefaultAvatar(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.getUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User updatedUser = avatarService.resetToDefaultAvatar(user.getId());

        AvatarAssignmentResponse response = new AvatarAssignmentResponse(
                updatedUser.getId(),
                updatedUser.getEmail(),
                "Avatar por defecto",
                updatedUser.getAvatarUrl(),
                "Avatar restablecido correctamente"
        );

        return ResponseEntity.ok(response);
    }

}
