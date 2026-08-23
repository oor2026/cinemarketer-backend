package com.example.demo.web.controllers;

import com.example.demo.application.dtos.FollowDto;
import com.example.demo.application.dtos.UserProfileResponse;
import com.example.demo.domain.follow.UserFollowRepository;
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
import com.example.demo.domain.series.SeriesReviewRepository;
import com.example.demo.domain.series.SeriesCommentRepository;
import com.example.demo.domain.recommendation.SeriesRecommendationRepository;
import com.example.demo.domain.sweepstake.SweepstakeEntryRepository;
import com.example.demo.domain.sweepstake.WinnerRepository;
import com.example.demo.domain.user.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
    // Inyectar en constructor
    private final UserBlockRepository userBlockRepository;
    private final UserReportRepository userReportRepository;
    private final UserFollowRepository userFollowRepository;
    private final com.example.demo.domain.recommendation.MovieRecommendationRepository recommendationRepository;
    private final com.example.demo.domain.pointtransaction.PointTransactionRepository pointTransactionRepository;
    private final com.example.demo.domain.publication.PublicationRepository publicationRepository;
    private final com.example.demo.domain.comment.CommentReactionRepository commentReactionRepository;
    private final SeriesReviewRepository seriesReviewRepository;
    private final SeriesCommentRepository seriesCommentRepository;
    private final SeriesRecommendationRepository seriesRecommendationRepository;

    // ==============================================
    // DEPENDENCIAS
    // ==============================================
    private final UserService userService;
    private final LevelCalculatorService levelCalculatorService;
    private final PointBatchRepository pointBatchRepository;
    private final AvatarService avatarService;
    private final com.example.demo.application.services.NombreReservadoService nombreReservadoService;
    private final com.example.demo.application.services.MoviePersistenceService moviePersistenceService;
    private final com.example.demo.application.services.SeriesPersistenceService seriesPersistenceService;

    public UserController(
            UserRepository userRepository,
            ReviewRepository reviewRepository,
            RedemptionRepository redemptionRepository,
            CommentRepository commentRepository,
            SweepstakeEntryRepository sweepstakeEntryRepository,
            WinnerRepository winnerRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            UserDeletionService userDeletionService, com.example.demo.domain.comment.CommentReactionRepository commentReactionRepository, SeriesReviewRepository seriesReviewRepository, SeriesCommentRepository seriesCommentRepository,
            UserService userService,
            LevelCalculatorService levelCalculatorService,
            AvatarService avatarService,
            PointBatchRepository pointBatchRepository,
            UserBlockRepository userBlockRepository,
            UserReportRepository userReportRepository,
            UserFollowRepository userFollowRepository, com.example.demo.domain.recommendation.MovieRecommendationRepository recommendationRepository, com.example.demo.domain.pointtransaction.PointTransactionRepository pointTransactionRepository, com.example.demo.domain.publication.PublicationRepository publicationRepository, SeriesRecommendationRepository seriesRecommendationRepository, com.example.demo.application.services.NombreReservadoService nombreReservadoService, com.example.demo.application.services.MoviePersistenceService moviePersistenceService, com.example.demo.application.services.SeriesPersistenceService seriesPersistenceService) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.redemptionRepository = redemptionRepository;
        this.commentRepository = commentRepository;
        this.sweepstakeEntryRepository = sweepstakeEntryRepository;
        this.winnerRepository = winnerRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.userDeletionService = userDeletionService;
        this.commentReactionRepository = commentReactionRepository;
        this.seriesReviewRepository = seriesReviewRepository;
        this.seriesCommentRepository = seriesCommentRepository;
        this.userService = userService;
        this.levelCalculatorService = levelCalculatorService;
        this.pointBatchRepository = pointBatchRepository;
        this.avatarService = avatarService;
        this.userBlockRepository = userBlockRepository;
        this.userReportRepository = userReportRepository;
        this.userFollowRepository = userFollowRepository;
        this.recommendationRepository = recommendationRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.publicationRepository = publicationRepository;
        this.seriesRecommendationRepository = seriesRecommendationRepository;
        this.nombreReservadoService = nombreReservadoService;
        this.moviePersistenceService = moviePersistenceService;
        this.seriesPersistenceService = seriesPersistenceService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        User user = getAuthenticatedUser();

        // Cada métrica suma Películas + Series — son entidades separadas
        // (SeriesReview/SeriesComment/SeriesRecommendation no reutilizan las
        // tablas de Películas), así que hay que consultar las dos y sumar.
        long reviewsCount          = reviewRepository.countByUserId(user.getId())
                + seriesReviewRepository.countByUserId(user.getId());
        long redemptionsCount      = redemptionRepository.countByUserId(user.getId());
        long commentsCount         = commentRepository.countCommentsByUserId(user.getId())
                + seriesCommentRepository.countCommentsByUserId(user.getId());
        long recommendacionesCount = recommendationRepository.countBySenderId(user.getId())
                + seriesRecommendationRepository.countBySenderId(user.getId());
        long merecePuntosCount     = pointTransactionRepository.countByUserIdAndAction(
                user.getId(), com.example.demo.domain.point.PointAction.RECEIVE_MERECE_PUNTO);
        long publicationsCount     = publicationRepository.countByUserIdAndHiddenFalse(user.getId());

        // ==============================================
        // Cálculos EXCLUSIVOS del sistema de insignias — mismos métodos que
        // usa LevelCalculatorService para el ascenso real, para que el modal
        // nunca pueda desincronizarse de la lógica real de promoción.
        // ==============================================
        long commentsUniqueMoviesCount = commentRepository.countDistinctMoviesCommentedByUser(user.getId());
        long usuariosSeguidosCount     = userFollowRepository.countByFollowerId(user.getId());
        long seguidoresGanadosCount    = userFollowRepository.countByFollowingId(user.getId());
        long teBancoRecibidosCount     = commentReactionRepository.countDistinctBancoGiversForUser(user.getId());

        int diasActivos = 0;
        if (user.getCreatedAt() != null && user.getLastLoginAt() != null) {
            diasActivos = (int) java.time.temporal.ChronoUnit.DAYS.between(
                    user.getCreatedAt().toLocalDate(),
                    user.getLastLoginAt().toLocalDate()
            );
        }

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
        response.setId(user.getId());
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
        response.setRecommendationsCount((int) recommendacionesCount);
        response.setMerecePuntosCount((int) merecePuntosCount);
        response.setPublicationsCount((int) publicationsCount);

        // Campos exclusivos del sistema de insignias
        response.setCommentsUniqueMoviesCount((int) commentsUniqueMoviesCount);
        response.setUsuariosSeguidosCount((int) usuariosSeguidosCount);
        response.setSeguidoresGanadosCount((int) seguidoresGanadosCount);
        response.setTeBancoRecibidosCount((int) teBancoRecibidosCount);
        response.setDiasActivos(diasActivos);

        response.setDni(user.getDni());
        response.setPhone(user.getPhone());
        response.setBirthDate(user.getBirthDate());
        response.setSexo(user.getSexo());
        response.setProvincia(user.getProvincia());
        response.setLocalidad(user.getLocalidad());

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
        response.setCreator(user.isActiveCreator());
        response.setCreatorUntil(user.getCreatorUntil());
        response.setProfileVisibility(user.getProfileVisibility());
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
            boolean esAdmin = user.getRole() == UserRole.ADMIN;
            // ® está reservado exclusivamente para el equipo de Cinemarketer
            if (name.contains("®") && !esAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "El carácter ® está reservado para el equipo de Cinemarketer."));
            }
            // "Cinemarketer" y variantes, mismo criterio que el símbolo de arriba
            if (nombreReservadoService.esNombreReservado(name) && !esAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Ese nombre está reservado para el equipo de Cinemarketer."));
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

        if (fields.containsKey("birthDate")) {
            String bd = fields.get("birthDate");
            user.setBirthDate(bd == null || bd.isBlank() ? null : java.time.LocalDate.parse(bd));
        }

        if (fields.containsKey("sexo")) {
            String s = fields.get("sexo").trim();
            user.setSexo(s.isEmpty() ? null : s);
        }

        if (fields.containsKey("provincia")) {
            String p = fields.get("provincia").trim();
            user.setProvincia(p.isEmpty() ? null : p);
        }

        if (fields.containsKey("localidad")) {
            String l = fields.get("localidad").trim();
            user.setLocalidad(l.isEmpty() ? null : l);
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

    /**
     * Sube un banner personalizado para el perfil público
     * POST /api/users/me/banner
     */
    @PostMapping(value = "/me/banner", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadBanner(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "El archivo no puede estar vacío"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Solo se permiten archivos de imagen"));
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "El archivo no puede superar los 2MB"));
        }

        try {
            User user = getAuthenticatedUser();
            User updatedUser = userService.uploadBanner(user.getId(), file);

            return ResponseEntity.ok(Map.of(
                    "message", "Banner actualizado correctamente",
                    "bannerUrl", updatedUser.getBannerUrl(),
                    "success", true
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message", "Error al subir el banner: " + e.getMessage(),
                            "success", false
                    ));
        }
    }

    /**
     * Actualiza la biografía del perfil público
     * PATCH /api/users/me/bio
     */
    @PatchMapping("/me/bio")
    public ResponseEntity<?> updateBio(@RequestBody Map<String, String> body) {
        try {
            User user = getAuthenticatedUser();

            String bioTitulo = body.getOrDefault("bioTitulo", "").trim();
            String bioTexto  = body.getOrDefault("bioTexto", "").trim();

            if (bioTitulo.length() > 50) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "El título no puede superar los 50 caracteres"));
            }
            if (bioTexto.length() > 255) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "La descripción no puede superar los 255 caracteres"));
            }

            user.setBioTitulo(bioTitulo.isEmpty() ? null : bioTitulo);
            user.setBioTexto(bioTexto.isEmpty() ? null : bioTexto);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Biografía actualizada correctamente",
                    "bioTitulo", bioTitulo,
                    "bioTexto", bioTexto,
                    "success", true
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al actualizar la biografía", "success", false));
        }
    }

    /**
     * Actualiza la película favorita del perfil público
     * PATCH /api/users/me/pelicula-favorita
     */
    @PatchMapping("/me/pelicula-favorita")
    public ResponseEntity<?> updatePeliculaFavorita(@RequestBody Map<String, Integer> body) {
        try {
            User user = getAuthenticatedUser();

            Integer movieId = body.get("movieId");
            if (movieId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Falta el ID de la película"));
            }

            // Persiste la película localmente si todavía no existe (por ej.
            // si el usuario nunca la votó/comentó) — así el perfil no
            // depende de pegarle a TMDb en vivo cada vez que alguien lo mira.
            moviePersistenceService.obtenerOCrearPelicula(movieId.longValue());

            user.setPeliculaFavoritaId(movieId);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Película favorita actualizada correctamente",
                    "peliculaFavoritaId", movieId,
                    "success", true
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al actualizar la película favorita", "success", false));
        }
    }

    @PatchMapping("/me/ultima-vista-cine")
    public ResponseEntity<?> updateUltimaVistaCine(@RequestBody Map<String, Integer> body) {
        try {
            User user = getAuthenticatedUser();
            Integer movieId = body.get("movieId");
            if (movieId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Falta el ID de la película"));
            }
            moviePersistenceService.obtenerOCrearPelicula(movieId.longValue());
            user.setUltimaVistaCineId(movieId);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Actualizado correctamente", "ultimaVistaCineId", movieId, "success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al actualizar", "success", false));
        }
    }

    @PatchMapping("/me/no-me-canso-de-ver")
    public ResponseEntity<?> updateNoMeCansoDeVer(@RequestBody Map<String, Integer> body) {
        try {
            User user = getAuthenticatedUser();
            Integer movieId = body.get("movieId");
            if (movieId == null) return ResponseEntity.badRequest().body(Map.of("message", "Falta el ID de la película"));
            moviePersistenceService.obtenerOCrearPelicula(movieId.longValue());
            user.setNoMeCansoDeVerId(movieId);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Actualizado correctamente", "noMeCansoDeVerId", movieId, "success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al actualizar", "success", false));
        }
    }

    @PatchMapping("/me/no-la-banco")
    public ResponseEntity<?> updateNoLaBanco(@RequestBody Map<String, Integer> body) {
        try {
            User user = getAuthenticatedUser();
            Integer movieId = body.get("movieId");
            if (movieId == null) return ResponseEntity.badRequest().body(Map.of("message", "Falta el ID de la película"));
            moviePersistenceService.obtenerOCrearPelicula(movieId.longValue());
            user.setNoLaBancoId(movieId);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Actualizado correctamente", "noLaBancoId", movieId, "success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al actualizar", "success", false));
        }
    }

    // ── Equivalentes de Series de los 4 endpoints de arriba ──────────────

    @PatchMapping("/me/serie-favorita")
    public ResponseEntity<?> updateSerieFavorita(@RequestBody Map<String, Integer> body) {
        try {
            User user = getAuthenticatedUser();
            Integer seriesId = body.get("seriesId");
            if (seriesId == null) return ResponseEntity.badRequest().body(Map.of("message", "Falta el ID de la serie"));
            seriesPersistenceService.obtenerOCrearSerie(seriesId.longValue());
            user.setSerieFavoritaId(seriesId);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Serie favorita actualizada correctamente", "serieFavoritaId", seriesId, "success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al actualizar la serie favorita", "success", false));
        }
    }

    @PatchMapping("/me/ultima-maraton")
    public ResponseEntity<?> updateUltimaMaraton(@RequestBody Map<String, Integer> body) {
        try {
            User user = getAuthenticatedUser();
            Integer seriesId = body.get("seriesId");
            if (seriesId == null) return ResponseEntity.badRequest().body(Map.of("message", "Falta el ID de la serie"));
            seriesPersistenceService.obtenerOCrearSerie(seriesId.longValue());
            user.setUltimaMaratonId(seriesId);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Actualizado correctamente", "ultimaMaratonId", seriesId, "success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al actualizar", "success", false));
        }
    }

    @PatchMapping("/me/no-me-canso-de-ver-serie")
    public ResponseEntity<?> updateNoMeCansoDeVerSerie(@RequestBody Map<String, Integer> body) {
        try {
            User user = getAuthenticatedUser();
            Integer seriesId = body.get("seriesId");
            if (seriesId == null) return ResponseEntity.badRequest().body(Map.of("message", "Falta el ID de la serie"));
            seriesPersistenceService.obtenerOCrearSerie(seriesId.longValue());
            user.setNoMeCansoDeVerSerieId(seriesId);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Actualizado correctamente", "noMeCansoDeVerSerieId", seriesId, "success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al actualizar", "success", false));
        }
    }

    @PatchMapping("/me/no-la-banco-serie")
    public ResponseEntity<?> updateNoLaBancoSerie(@RequestBody Map<String, Integer> body) {
        try {
            User user = getAuthenticatedUser();
            Integer seriesId = body.get("seriesId");
            if (seriesId == null) return ResponseEntity.badRequest().body(Map.of("message", "Falta el ID de la serie"));
            seriesPersistenceService.obtenerOCrearSerie(seriesId.longValue());
            user.setNoLaBancoSerieId(seriesId);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Actualizado correctamente", "noLaBancoSerieId", seriesId, "success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al actualizar", "success", false));
        }
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

    @PostMapping("/{id}/unblock")
    @Transactional
    public ResponseEntity<?> unblockUser(@PathVariable Long id) {
        User me = getAuthenticatedUser();
        userBlockRepository.findByBlockerIdAndBlockedId(me.getId(), id)
                .ifPresent(userBlockRepository::delete);
        return ResponseEntity.ok(Map.of("unblocked", true));
    }

    private User getAuthenticatedUser() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    // GET /api/users/search?q=oscar
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (q == null || q.trim().isEmpty())
            return ResponseEntity.ok(Map.of("users", List.of(), "hasMore", false));

        Pageable pageable = PageRequest.of(page, size + 1);
        Page<User> pageResult = userRepository
                .findByNameContainingIgnoreCaseAndActiveTrue(q.trim(), pageable);

        List<FollowDto> users = pageResult.getContent()
                .stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> !u.getName().contains("®"))
                .limit(size)
                .map(u -> new FollowDto(
                        u.getId(),
                        u.getName(),
                        u.getEffectiveAvatarUrl(),
                        u.getLevel() != null ? u.getLevel().name() : "AMATEUR",
                        u.getLevel() != null ? u.getLevel().getEmoji() : "🎬"
                ))
                .toList();

        boolean hasMore = pageResult.getContent().size() > size;

        return ResponseEntity.ok(Map.of("users", users, "hasMore", hasMore));
    }

    /**
     * POST /api/users/{id}/block
     * Bloquea un usuario y opcionalmente lo reporta
     */
    @PostMapping("/{id}/block")
    @Transactional
    public ResponseEntity<?> blockUser(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        User me = getAuthenticatedUser();

        if (me.getId().equals(id))
            return ResponseEntity.badRequest().body(Map.of("error", "No podés bloquearte a vos mismo"));

        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Bloquear si no está ya bloqueado
        if (!userBlockRepository.existsByBlockerIdAndBlockedId(me.getId(), id)) {
            userBlockRepository.save(new UserBlock(me, target));
        }

        // Eliminar follows en ambas direcciones
        userFollowRepository.findByFollowerIdAndFollowingId(me.getId(), id)
                .ifPresent(userFollowRepository::delete);
        userFollowRepository.findByFollowerIdAndFollowingId(id, me.getId())
                .ifPresent(userFollowRepository::delete);

        // Reportar si el usuario lo pidió
        boolean reportar = Boolean.TRUE.equals(body.get("reportar"));
        if (reportar && !userReportRepository.existsByReporterIdAndReportedId(me.getId(), id)) {
            String reason = (String) body.getOrDefault("reason", "Reportado al bloquear");
            userReportRepository.save(new UserReport(me, target, reason));
        }

        return ResponseEntity.ok(Map.of("blocked", true, "reported", reportar));
    }

    /**
     * GET /api/users/me/blocked
     * Devuelve la lista de usuarios bloqueados por el usuario autenticado
     */
    @GetMapping("/me/blocked")
    public ResponseEntity<?> getBlockedUsers() {
        User me = getAuthenticatedUser();
        List<Map<String, Object>> bloqueados = userBlockRepository
                .findByBlockerId(me.getId())
                .stream()
                .map(block -> {
                    User blocked = block.getBlocked();
                    Map<String, Object> dto = new java.util.HashMap<>();
                    dto.put("id", blocked.getId());
                    dto.put("name", blocked.getName());
                    dto.put("avatarUrl", blocked.getEffectiveAvatarUrl());
                    return dto;
                })
                .toList();
        return ResponseEntity.ok(bloqueados);
    }
}