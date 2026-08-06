package com.example.demo.web.controllers;

import com.example.demo.application.services.AdminNotificationService;
import com.example.demo.domain.notification.AdminNotificationCampaign;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;
    private final UserRepository userRepository;

    public AdminNotificationController(AdminNotificationService adminNotificationService,
                                       UserRepository userRepository) {
        this.adminNotificationService = adminNotificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<AdminNotificationCampaign>> listar() {
        return ResponseEntity.ok(adminNotificationService.listar());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body, Authentication auth) {
        String title = (String) body.get("title");
        String message = (String) body.get("message");
        String targetType = (String) body.get("targetType"); // SEGMENTS | USERS

        @SuppressWarnings("unchecked")
        List<String> segments = (List<String>) body.get("segments");

        @SuppressWarnings("unchecked")
        List<Number> userIdsRaw = (List<Number>) body.get("userIds");
        List<Long> userIds = userIdsRaw != null
                ? userIdsRaw.stream().map(Number::longValue).toList() : null;

        LocalDateTime scheduledAt = null;
        Object scheduledAtRaw = body.get("scheduledAt");
        if (scheduledAtRaw instanceof String s && !s.isBlank()) {
            scheduledAt = LocalDateTime.parse(s);
        }

        if (title == null || title.isBlank() || message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Título y mensaje son obligatorios"));
        }
        if ("SEGMENTS".equals(targetType) && (segments == null || segments.isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Elegí al menos un segmento"));
        }
        if ("USERS".equals(targetType) && (userIds == null || userIds.isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Agregá al menos un usuario"));
        }

        AdminNotificationCampaign campaign = adminNotificationService.crear(
                title, message, targetType, segments, userIds, scheduledAt, auth.getName());

        return ResponseEntity.ok(campaign);
    }

    // Buscador para el selector "Usuarios" — reusa la misma consulta real
    // (con LIKE y paginación) que ya usa la sección Usuarios del admin.
    @GetMapping("/users/search")
    public ResponseEntity<List<Map<String, Object>>> buscarUsuarios(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Page<User> resultados = userRepository.findByNameContainingOrEmailContaining(
                q, q, org.springframework.data.domain.PageRequest.of(0, 20));

        return ResponseEntity.ok(resultados.getContent().stream().map(u -> Map.<String, Object>of(
                "id", u.getId(), "name", u.getName(), "email", u.getEmail()
        )).toList());
    }
}