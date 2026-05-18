package com.example.demo.web.controllers;

import com.example.demo.application.dtos.UserProfileResponse;
import com.example.demo.domain.pointbatch.PointBatch;
import com.example.demo.domain.pointbatch.PointBatchRepository;
import com.example.demo.application.dtos.UserLevelWithAvatarDto;
import com.example.demo.application.services.AvatarService;
import com.example.demo.application.services.EmailService;
import com.example.demo.application.services.UserDeletionService;
import com.example.demo.application.services.UserService;
import com.example.demo.application.services.LevelCalculatorService;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.sweepstake.SweepstakeEntryRepository;
import com.example.demo.domain.sweepstake.WinnerRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final RedemptionRepository redemptionRepository;
    private final CommentRepository commentRepository;
    private final SweepstakeEntryRepository sweepstakeEntryRepository;
    private final WinnerRepository winnerRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final UserDeletionService userDeletionService;

    // ==============================================
    // DEPENDENCIAS
    // ==============================================
    private final UserService userService;
    private final LevelCalculatorService levelCalculatorService;
    private final PointBatchRepository pointBatchRepository;
    private final AvatarService avatarService;

    public UserController(
            UserRepository userRepository,
            ReviewRepository reviewRepository,
            RedemptionRepository redemptionRepository,
            CommentRepository commentRepository,
            SweepstakeEntryRepository sweepstakeEntryRepository,
            WinnerRepository winnerRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            UserDeletionService userDeletionService,
            UserService userService,
            LevelCalculatorService levelCalculatorService,
            AvatarService avatarService,
            PointBatchRepository pointBatchRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.redemptionRepository = redemptionRepository;
        this.commentRepository = commentRepository;
        this.sweepstakeEntryRepository = sweepstakeEntryRepository;
        this.winnerRepository = winnerRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.userDeletionService = userDeletionService;
        this.userService = userService;
        this.levelCalculatorService = levelCalculatorService;
        this.pointBatchRepository = pointBatchRepository;
        this.avatarService = avatarService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        User user = getAuthenticatedUser();

        long reviewsCount     = reviewRepository.countByUserId(user.getId());
        long redemptionsCount = redemptionRepository.countByUserId(user.getId());
        long commentsCount    = commentRepository.countCommentsByUserId(user.getId());

        // Puntos próximos a vencer (lotes FREE con expiry <= 30 días)
        int expiringPts = 0;
        if (!user.isActivePremium()) {
            java.time.LocalDateTime in30 = java.time.LocalDateTime.now().plusDays(30);
            expiringPts = pointBatchRepository.findActiveBatchesByUserId(user.getId())
                    .stream()
                    .filter(b -> b.getExpiresAt() != null && b.getExpiresAt().isBefore(in30))
                    .mapToInt(PointBatch::getRemainingPoints)
                    .sum();
        }

        UserProfileResponse response = new UserProfileResponse();
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        response.setRole(user.getRole().name());
        response.setAvailablePoints(user.getAvailablePoints());
        response.setAccumulatedPoints(user.getAccumulatedPoints());
        response.setTotalRedeemedPoints(user.getTotalRedeemedPoints());
        response.setExpiringPoints(expiringPts);
        response.setTotalPoints(user.getAvailablePoints());
        response.setEmailVerified(user.isEmailVerified());
        response.setCreatedAt(user.getCreatedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setReviewsCount((int) reviewsCount);
        response.setRedemptionsCount((int) redemptionsCount);
        response.setCommentsCount((int) commentsCount);
        response.setDni(user.getDni());
        response.setPhone(user.getPhone());

        // ==============================================
        // CAMPOS DE AVATAR Y NIVEL
        // ==============================================
        response.setAvatarUrl(user.getEffectiveAvatarUrl());

        // Nombre del avatar seleccionado (null si es personalizado o no encontrado en BD)
        avatarService.getAvatarNameByUrl(user.getEffectiveAvatarUrl())
                .ifPresent(response::setAvatarName);

        response.setLevel(user.getLevel());
        response.setLevelDisplayName(user.getLevel().getDisplayName());
        response.setLevelEmoji(user.getLevel().getEmoji());
        response.setLevelUpdatedAt(user.getLevelUpdatedAt());

        // Calcular progreso hacia el siguiente nivel
        response.setLevelProgress(levelCalculatorService.getProgressToNextLevel(user));
        response.setPointsToNextLevel(levelCalculatorService.getPointsToNextLevel(user));
        response.setCanLevelUp(levelCalculatorService.canLevelUp(user));
        response.setPremium(user.isActivePremium());
        response.setPremiumUntil(user.getPremiumUntil());
        response.setGoogleId(user.getGoogleId());

        com.example.demo.domain.user.UserLevel nextLvl = user.getLevel().getNextLevel();
        if (nextLvl != null) {
            response.setNextLevel(nextLvl);
            response.setNextLevelDisplayName(nextLvl.getDisplayName());
        }

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<Map<String, String>> updateCurrentUser(@RequestBody Map<String, String> fields) {
        User user = getAuthenticatedUser();
        boolean emailChanged = false;

        if (fields.containsKey("name")) {
            String name = fields.get("name").trim();
            if (name.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "El nombre no puede estar vacío."));
            }
            user.setName(name);
        }

        if (fields.containsKey("email")) {
            String newEmail = fields.get("email").trim().toLowerCase();
            if (!newEmail.equals(user.getEmail())) {
                if (userRepository.existsByEmail(newEmail)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("message", "El email ya está siendo utilizado por otra cuenta."));
                }
                user.setEmail(newEmail);
                user.setEmailVerified(false);
                user.setVerificationToken(UUID.randomUUID().toString());
                emailChanged = true;
            }
        }

        if (fields.containsKey("phone")) {
            String phone = fields.get("phone").trim();
            if (phone.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "El teléfono no puede estar vacío."));
            }
            user.setPhone(phone);
        }

        if (fields.containsKey("dni")) {
            String dni = fields.get("dni").trim();
            user.setDni(dni);
        }

        userRepository.save(user);

        if (emailChanged) {
            try {
                emailService.sendEmailChangeVerification(user.getEmail(), user.getVerificationToken());
            } catch (Exception e) {

            }
            return ResponseEntity.ok(Map.of(
                    "message", "email_changed",
                    "email", user.getEmail()
            ));
        }

        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteCurrentUser(@RequestBody Map<String, String> body) {
        String password = body.getOrDefault("password", "").trim();

        if (password.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "La contraseña es obligatoria para confirmar la eliminación."));
        }

        User user = getAuthenticatedUser();

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "La contraseña ingresada es incorrecta."));
        }

        // Guardar datos antes de eliminar
        String userEmail = user.getEmail();
        String userName = user.getName();

        userDeletionService.deleteAllUserData(user);

        // Disparar mail de confirmación
        try {
            emailService.sendAccountDeletionEmail(userEmail, userName);
        } catch (Exception e) {
            // No interrumpir el flujo si el mail falla
        }

        return ResponseEntity.ok(Map.of("message", "Cuenta eliminada exitosamente."));
    }

    // ── Cambiar contraseña ────────────────────────────────────────────────────
    @PatchMapping("/me/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body) {
        User user = getAuthenticatedUser();

        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");

        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Datos inválidos."));
        }

        if (!newPassword.matches("(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d@!_-]{8,}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número. Solo se permiten letras, números y los caracteres @ ! - _"));
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "La contraseña actual es incorrecta."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
    }

    // ==============================================
    // ENDPOINTS PARA AVATAR
    // ==============================================

    /**
     * Actualiza el avatar del usuario (avatar predefinido)
     * POST /api/users/me/avatar/{avatarId}
     */
    @PostMapping("/me/avatar/{avatarId}")
    public ResponseEntity<?> updateAvatar(@PathVariable Long avatarId) {
        User user = getAuthenticatedUser();
        User updatedUser = userService.updateAvatar(user.getId(), avatarId);
        return ResponseEntity.ok(Map.of(
                "message", "Avatar actualizado correctamente",
                "avatarUrl", updatedUser.getAvatarUrl()
        ));
    }

    /**
     * Sube un avatar personalizado con validaciones
     * POST /api/users/me/avatar/upload
     */
    @PostMapping(value = "/me/avatar/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadCustomAvatar(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "El archivo no puede estar vacío"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Solo se permiten archivos de imagen (JPEG, PNG, GIF, etc.)"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "El archivo no puede superar los 5MB"));
        }

        try {
            User user = getAuthenticatedUser();
            User updatedUser = userService.uploadCustomAvatar(user.getId(), file);

            return ResponseEntity.ok(Map.of(
                    "message", "Avatar personalizado subido correctamente",
                    "avatarUrl", updatedUser.getAvatarUrl(),
                    "success", true
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message", "Error al subir el avatar: " + e.getMessage(),
                            "success", false
                    ));
        }
    }

    /**
     * Restablece al avatar por defecto del nivel actual
     * POST /api/users/me/avatar/reset
     */
    @PostMapping("/me/avatar/reset")
    public ResponseEntity<?> resetToDefaultAvatar() {
        User user = getAuthenticatedUser();
        User updatedUser = userService.resetToDefaultAvatar(user.getId());
        return ResponseEntity.ok(Map.of(
                "message", "Avatar restablecido correctamente",
                "avatarUrl", updatedUser.getAvatarUrl()
        ));
    }

    /**
     * Elimina el avatar personalizado (vuelve al por defecto)
     * DELETE /api/users/me/avatar
     */
    @DeleteMapping("/me/avatar")
    public ResponseEntity<?> removeAvatar() {
        User user = getAuthenticatedUser();
        user.removeAvatar();
        userRepository.save(user);

        User updatedUser = userService.resetToDefaultAvatar(user.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Avatar eliminado",
                "avatarUrl", updatedUser.getAvatarUrl()
        ));
    }

    // ==============================================
    // ENDPOINTS PARA NIVEL
    // ==============================================

    /**
     * Obtiene información detallada del nivel y progreso
     * GET /api/users/me/level
     */
    @GetMapping("/me/level")
    public ResponseEntity<UserLevelWithAvatarDto> getLevelInfo() {
        User user = getAuthenticatedUser();

        UserLevelWithAvatarDto dto = new UserLevelWithAvatarDto();
        dto.setUserId(user.getId());
        dto.setUserName(user.getName());
        dto.setCurrentLevel(user.getLevel());
        dto.setCurrentLevelDisplay(user.getLevel().getDisplayName());
        dto.setCurrentLevelEmoji(user.getLevel().getEmoji());

        com.example.demo.domain.user.UserLevel nextLvl = user.getLevel().getNextLevel();
        if (nextLvl != null) {
            dto.setNextLevel(nextLvl);
            dto.setNextLevelDisplay(nextLvl.getDisplayName());
            dto.setNextLevelEmoji(nextLvl.getEmoji());
            dto.setProgress(levelCalculatorService.getProgressToNextLevel(user));
            dto.setPointsToNextLevel(levelCalculatorService.getPointsToNextLevel(user));
            dto.setCanLevelUp(levelCalculatorService.canLevelUp(user));
        }

        return ResponseEntity.ok(dto);
    }

    /**
     * Solicita recalcular el nivel del usuario
     * POST /api/users/me/level/recalculate
     */
    @PostMapping("/me/level/recalculate")
    public ResponseEntity<?> recalculateLevel() {
        User user = getAuthenticatedUser();
        User updatedUser = userService.recalculateLevel(user.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Nivel recalculado",
                "level", updatedUser.getLevel().name(),
                "levelDisplay", updatedUser.getLevel().getDisplayName(),
                "levelEmoji", updatedUser.getLevel().getEmoji()
        ));
    }

    private User getAuthenticatedUser() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}