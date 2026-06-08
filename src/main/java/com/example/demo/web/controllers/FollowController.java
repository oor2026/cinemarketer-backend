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

    // POST /api/follows/{userId} — seguir usuario
    @PostMapping("/{userId}")
    @Transactional
    public ResponseEntity<?> follow(@PathVariable Long userId,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        if (me.getId().equals(userId))
            return ResponseEntity.badRequest().body(Map.of("error", "No podés seguirte a vos mismo"));

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (followRepository.existsByFollowerIdAndFollowingId(me.getId(), userId))
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya seguís a este usuario"));

        followRepository.save(new UserFollow(me, target));

        // Notificar al usuario seguido
        Notification notif = new Notification();
        notif.setUser(target);
        notif.setActorName(me.getName());
        notif.setActorId(me.getId());
        notif.setType(NotificationType.NEW_FOLLOWER);
        notif.setMessage(me.getName() + " comenzó a seguirte");
        notificationRepository.save(notif);

        return ResponseEntity.ok(Map.of(
                "following", true,
                "followersCount", followRepository.countByFollowingId(userId)
        ));
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
}