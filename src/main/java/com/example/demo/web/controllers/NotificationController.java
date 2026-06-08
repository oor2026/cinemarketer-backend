package com.example.demo.web.controllers;

import com.example.demo.application.dtos.NotificationDto;
import com.example.demo.domain.notification.NotificationRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationController(NotificationRepository notificationRepository,
                                  UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // GET /api/notifications — ultimas 30 notificaciones
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<NotificationDto> dtos = notificationRepository
                .findTop30ByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(n -> new NotificationDto(
                        n.getId(), n.getType(), n.getMessage(),
                        n.getActorId(),
                        n.getMovieId(), n.getMovieTitle(), n.getCommentId(),
                        n.getReplyId(), n.isRead(), n.getCreatedAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // GET /api/notifications/unread-count — cantidad de no leidas
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        long count = notificationRepository.countByUserIdAndReadFalse(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    // POST /api/notifications/read-all — marcar todas como leidas
    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<?> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        notificationRepository.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of("message", "Todas las notificaciones marcadas como leídas"));
    }

    // POST /api/notifications/{id}/read — marcar una notificacion como leida
    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<?> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUser().getId().equals(user.getId())) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });

        return ResponseEntity.ok(Map.of("message", "Notificación marcada como leída"));
    }
}
