package com.example.demo.web.controllers;

import com.example.demo.application.dtos.*;
import com.example.demo.application.services.AvatarService;
import com.example.demo.domain.avatar.Avatar;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador admin para gestión de avatares.
 * Todos los endpoints bajo /api/admin/avatars, consistente con
 * AdminFaqController, AdminUserController, etc.
 * Seguridad manejada por SecurityConfig (.requestMatchers("/api/admin/**").hasRole("ADMIN"))
 */
@RestController
@RequestMapping("/api/admin/avatars")
public class AdminAvatarController {

    private final AvatarService avatarService;

    public AdminAvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    // ==============================================
    // LISTAR AVATARES (con paginación opcional)
    // GET /api/admin/avatars?page=0&size=20
    // ==============================================
    @GetMapping
    public ResponseEntity<?> getAllAvatars(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search) {

        List<Avatar> avatars = avatarService.getAllAvatars();

        // Filtro por categoría
        if (category != null && !category.isBlank()) {
            avatars = avatars.stream()
                    .filter(a -> category.equalsIgnoreCase(a.getCategory()))
                    .collect(Collectors.toList());
        }

        // Filtro por estado
        if (estado != null) {
            if (estado.equals("activos")) {
                avatars = avatars.stream()
                        .filter(Avatar::isActive)
                        .collect(Collectors.toList());
            } else if (estado.equals("inactivos")) {
                avatars = avatars.stream()
                        .filter(a -> !a.isActive())
                        .collect(Collectors.toList());
            }
        }

        // Filtro por búsqueda
        if (search != null && !search.isBlank()) {
            final String q = search.toLowerCase();
            avatars = avatars.stream()
                    .filter(a -> a.getName().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        // Paginación manual
        int total = avatars.size();
        int from = Math.min(page * size, total);
        int to   = Math.min(from + size, total);
        List<AvatarDto> paginados = avatars.subList(from, to)
                .stream()
                .map(AvatarDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{
            put("avatars", paginados);
            put("currentPage", page);
            put("totalPages", (int) Math.ceil((double) total / size));
            put("totalElements", total);
        }});
    }

    // ==============================================
    // OBTENER AVATAR POR ID
    // GET /api/admin/avatars/{id}
    // ==============================================
    @GetMapping("/{id}")
    public ResponseEntity<AvatarDetailDto> getAvatarById(@PathVariable Long id) {
        Avatar avatar = avatarService.getAvatarById(id);
        return ResponseEntity.ok(AvatarDetailDto.fromEntity(avatar));
    }

    // ==============================================
    // CREAR AVATAR
    // POST /api/admin/avatars
    // ==============================================
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<AvatarDto> createAvatar(
            @RequestPart("data") AvatarRequestDto request,
            @RequestPart("image") MultipartFile imageFile) {

        Avatar avatar = new Avatar();
        avatar.setName(request.getName());
        avatar.setCategory(request.getCategory());
        avatar.setRequiredLevel(request.getRequiredLevel());
        avatar.setSortOrder(request.getSortOrder());
        avatar.setActive(request.getActive() != null ? request.getActive() : true);
        avatar.setDefault(request.getIsDefault() != null ? request.getIsDefault() : false);

        Avatar created = avatarService.createAvatar(avatar, imageFile);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AvatarDto.fromEntity(created));
    }

    // ==============================================
    // ACTUALIZAR AVATAR
    // PUT /api/admin/avatars/{id}
    // ==============================================
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<AvatarDto> updateAvatar(
            @PathVariable Long id,
            @RequestPart("data") AvatarRequestDto request,
            @RequestPart(value = "image", required = false) MultipartFile imageFile) {

        Avatar avatarDetails = new Avatar();
        avatarDetails.setName(request.getName());
        avatarDetails.setCategory(request.getCategory());
        avatarDetails.setRequiredLevel(request.getRequiredLevel());
        avatarDetails.setSortOrder(request.getSortOrder());
        avatarDetails.setActive(request.getActive());
        avatarDetails.setDefault(request.getIsDefault());

        Avatar updated = avatarService.updateAvatar(id, avatarDetails, imageFile);
        return ResponseEntity.ok(AvatarDto.fromEntity(updated));
    }

    // ==============================================
    // DESACTIVAR AVATAR (soft delete)
    // DELETE /api/admin/avatars/{id}
    // ==============================================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAvatar(@PathVariable Long id) {
        avatarService.deleteAvatar(id);
        return ResponseEntity.noContent().build();
    }

    // ==============================================
    // ACTIVAR AVATAR
    // PATCH /api/admin/avatars/{id}/activate
    // ==============================================
    @PatchMapping("/{id}/activate")
    public ResponseEntity<AvatarDto> activateAvatar(@PathVariable Long id) {
        Avatar avatar = avatarService.activateAvatar(id);
        return ResponseEntity.ok(AvatarDto.fromEntity(avatar));
    }

    // ==============================================
    // CAMBIAR IMAGEN DE AVATAR
    // POST /api/admin/avatars/{id}/image
    // ==============================================
    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public ResponseEntity<AvatarDto> updateAvatarImage(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile imageFile) {

        // Obtener avatar existente y actualizar solo la imagen
        Avatar existing = avatarService.getAvatarById(id);
        Avatar updated = avatarService.updateAvatar(id, existing, imageFile);
        return ResponseEntity.ok(AvatarDto.fromEntity(updated));
    }

    // ==============================================
    // ESTADÍSTICAS
    // GET /api/admin/avatars/stats
    // ==============================================
    @GetMapping("/stats")
    public ResponseEntity<AvatarService.AvatarStats> getAvatarStats() {
        return ResponseEntity.ok(avatarService.getAvatarStats());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<AvatarDto> deactivateAvatar(@PathVariable Long id) {
        Avatar avatar = avatarService.deactivateAvatar(id);
        return ResponseEntity.ok(AvatarDto.fromEntity(avatar));
    }
}
