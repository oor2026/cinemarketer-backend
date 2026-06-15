package com.example.demo.web.controllers;

import com.example.demo.application.dtos.FollowDto;
import com.example.demo.domain.follow.UserFollow;
import com.example.demo.domain.follow.UserFollowRepository;
import com.example.demo.domain.notification.Notification;
import com.example.demo.domain.notification.NotificationRepository;
import com.example.demo.domain.notification.NotificationType;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final UserFollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public FollowController(UserFollowRepository followRepository,
                            UserRepository userRepository,
                            NotificationRepository notificationRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    // POST /api/follows/{userId} — seguir usuario o enviar invitación
    @PostMapping("/{userId}")
    @Transactional
    public ResponseEntity<?> follow(@PathVariable Long userId,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        if (me.getId().equals(userId))
            return ResponseEntity.badRequest().body(Map.of("error", "No podés seguirte a vos mismo"));

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Si ya existe un follow (cualquier estado), no hacer nada
        if (followRepository.existsByFollowerIdAndFollowingId(me.getId(), userId))
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya seguís o tenés una invitación pendiente"));

        boolean esPrivado = target.isPrivate();
        UserFollow follow = new UserFollow(me, target);
        follow.setStatus(esPrivado ? "PENDING" : "ACCEPTED");
        followRepository.save(follow);

        if (esPrivado) {
            // Notificación de solicitud de seguimiento
            Notification notif = new Notification();
            notif.setUser(target);
            notif.setActorName(me.getName());
            notif.setActorId(me.getId());
            notif.setType(NotificationType.FOLLOW_REQUEST);
            notif.setMessage(me.getName() + " quiere seguirte");
            notif.setRead(false);
            notif.setCreatedAt(java.time.LocalDateTime.now());
            notificationRepository.save(notif);

            return ResponseEntity.ok(Map.of(
                    "status", "PENDING",
                    "followersCount", followRepository.countByFollowingIdAndStatus(userId, "ACCEPTED")
            ));
        } else {
            // Notificación de nuevo seguidor
            Notification notif = notificationRepository
                    .findTopByUserIdAndActorIdAndTypeOrderByCreatedAtDesc(target.getId(), me.getId(), NotificationType.NEW_FOLLOWER)
                    .orElse(new Notification());
            notif.setUser(target);
            notif.setActorName(me.getName());
            notif.setActorId(me.getId());
            notif.setType(NotificationType.NEW_FOLLOWER);
            notif.setMessage(me.getName() + " comenzó a seguirte");
            notif.setRead(false);
            notif.setCreatedAt(java.time.LocalDateTime.now());
            notificationRepository.save(notif);

            return ResponseEntity.ok(Map.of(
                    "status", "ACCEPTED",
                    "followersCount", followRepository.countByFollowingIdAndStatus(userId, "ACCEPTED")
            ));
        }
    }

    // DELETE /api/follows/{userId} — dejar de seguir
    @DeleteMapping("/{userId}")
    @Transactional
    public ResponseEntity<?> unfollow(@PathVariable Long userId,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        followRepository.findByFollowerIdAndFollowingId(me.getId(), userId)
                .ifPresent(followRepository::delete);

        return ResponseEntity.ok(Map.of(
                "following", false,
                "followersCount", followRepository.countByFollowingId(userId)
        ));
    }

    // GET /api/follows/following — lista de usuarios que sigo
    @GetMapping("/following")
    public ResponseEntity<List<FollowDto>> getFollowing(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<FollowDto> result = followRepository.findFollowingByUserId(me.getId())
                .stream()
                .map(f -> toDto(f.getFollowing()))
                .toList();
        return ResponseEntity.ok(result);
    }

    // GET /api/follows/followers — lista de mis seguidores
    @GetMapping("/followers")
    public ResponseEntity<List<FollowDto>> getFollowers(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<FollowDto> result = followRepository.findFollowersByUserId(me.getId())
                .stream()
                .filter(f -> "ACCEPTED".equals(f.getStatus()))
                .map(f -> toDto(f.getFollower()))
                .toList();
        return ResponseEntity.ok(result);
    }

    // GET /api/follows/{userId}/status — ¿sigo a este usuario?
    @GetMapping("/{userId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        boolean following = followRepository.existsByFollowerIdAndFollowingId(me.getId(), userId);
        long followers = followRepository.countByFollowingId(userId);
        long following_count = followRepository.countByFollowerId(userId);
        return ResponseEntity.ok(Map.of(
                "following", following,
                "followersCount", followers,
                "followingCount", following_count
        ));
    }

    // POST /api/follows/{followId}/accept — aceptar invitación
    @PostMapping("/{followId}/accept")
    @Transactional
    public ResponseEntity<?> acceptFollow(@PathVariable Long followId,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        UserFollow follow = followRepository.findById(followId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada"));

        if (!follow.getFollowing().getId().equals(me.getId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));

        follow.setStatus("ACCEPTED");
        followRepository.save(follow);

        // Actualizar la notificación FOLLOW_REQUEST original → convertirla en NEW_FOLLOWER
        notificationRepository
                .findTopByUserIdAndActorIdAndTypeOrderByCreatedAtDesc(
                        me.getId(), follow.getFollower().getId(), NotificationType.FOLLOW_REQUEST)
                .ifPresent(n -> {
                    n.setType(NotificationType.NEW_FOLLOWER);
                    n.setMessage(follow.getFollower().getName() + " comenzó a seguirte");
                    notificationRepository.save(n);
                });

        // Notificar al solicitante que fue aceptado
        Notification notif = new Notification();
        notif.setUser(follow.getFollower());
        notif.setActorName(me.getName());
        notif.setActorId(me.getId());
        notif.setType(NotificationType.FOLLOW_REQUEST_ACCEPTED);
        notif.setMessage(me.getName() + " aceptó tu solicitud de seguimiento");
        notif.setRead(false);
        notif.setCreatedAt(java.time.LocalDateTime.now());
        notificationRepository.save(notif);

        return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
    }

    // POST /api/follows/{followId}/reject — rechazar invitación
    @PostMapping("/{followId}/reject")
    @Transactional
    public ResponseEntity<?> rejectFollow(@PathVariable Long followId,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        UserFollow follow = followRepository.findById(followId)
                .orElseThrow(() -> new RuntimeException("Invitación no encontrada"));

        if (!follow.getFollowing().getId().equals(me.getId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));

        followRepository.delete(follow);

        // Eliminar la notificación de FOLLOW_REQUEST asociada
        notificationRepository
                .findTopByUserIdAndActorIdAndTypeOrderByCreatedAtDesc(
                        me.getId(), follow.getFollower().getId(), NotificationType.FOLLOW_REQUEST)
                .ifPresent(notificationRepository::delete);

        return ResponseEntity.ok(Map.of("status", "REJECTED"));
    }

    // PATCH /api/follows/privacy — cambiar visibilidad del perfil
    @PatchMapping("/privacy")
    @Transactional
    public ResponseEntity<?> updatePrivacy(@RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        String visibility = body.getOrDefault("visibility", "PUBLIC");
        if (!visibility.equals("PUBLIC") && !visibility.equals("PRIVATE"))
            return ResponseEntity.badRequest().body(Map.of("error", "Valor inválido"));

        me.setProfileVisibility(visibility);
        userRepository.save(me);

        return ResponseEntity.ok(Map.of("visibility", visibility));
    }

    // ── helpers ──────────────────────────────────────────────
    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private FollowDto toDto(User u) {
        return new FollowDto(
                u.getId(),
                u.getName(),
                u.getEffectiveAvatarUrl(),
                u.getLevel() != null ? u.getLevel().name() : "AMATEUR",
                u.getLevel() != null ? u.getLevel().getEmoji() : "🎬"
        );
    }

    // GET /api/follows/pending/{followerId}
    @GetMapping("/pending/{followerId}")
    public ResponseEntity<?> getPendingFollow(@PathVariable Long followerId,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        return followRepository.findByFollowerAndFollowing(followerId, me.getId())
                .filter(UserFollow::isPending)
                .map(f -> ResponseEntity.ok(Map.of(
                        "id", f.getId(),
                        "actorName", f.getFollower().getName()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}