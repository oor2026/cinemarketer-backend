package com.example.demo.web.controllers;

import com.example.demo.application.dtos.ReceivedRecommendationDto;
import com.example.demo.application.dtos.RecommendationRequest;
import com.example.demo.application.dtos.SuggestedUserDto;
import com.example.demo.application.services.MovieService;
import com.example.demo.application.services.NotificationService;
import com.example.demo.application.services.PointConfigService;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.notification.Notification;
import com.example.demo.domain.notification.NotificationRepository;
import com.example.demo.domain.notification.NotificationType;
import com.example.demo.domain.recommendation.MovieRecommendation;
import com.example.demo.domain.recommendation.MovieRecommendationRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final MovieRecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final MovieService movieService;
    private final PointConfigService pointConfigService;
    private final PointTransactionService pointTransactionService;

    public RecommendationController(MovieRecommendationRepository recommendationRepository,
                                    UserRepository userRepository,
                                    NotificationService notificationService,
                                    NotificationRepository notificationRepository,
                                    MovieService movieService,
                                    PointConfigService pointConfigService,
                                    PointTransactionService pointTransactionService) {
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.movieService = movieService;
        this.pointConfigService = pointConfigService;
        this.pointTransactionService = pointTransactionService;
    }

    // POST /api/recommendations — crear recomendación
    @PostMapping
    @Transactional
    public ResponseEntity<?> crear(@RequestBody RecommendationRequest req,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        if (me.getId().equals(req.getReceiverId()))
            return ResponseEntity.badRequest().body(Map.of("error", "No podés recomendarte a vos mismo"));

        User receiver = userRepository.findById(req.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Evitar duplicado
        if (recommendationRepository.existsBySenderIdAndReceiverIdAndMovieId(
                me.getId(), req.getReceiverId(), req.getMovieId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya le recomendaste esta película"));
        }

        MovieRecommendation rec = new MovieRecommendation();
        rec.setSender(me);
        rec.setReceiver(receiver);
        rec.setMovieId(req.getMovieId());
        try {
            var tmdb = movieService.getMovieDetails(req.getMovieId());
            if (tmdb != null) {
                rec.setMovieTitle(tmdb.getTitle());
                rec.setMoviePosterPath(tmdb.getPosterPath());
                rec.setMovieOverview(tmdb.getOverview());
            }
        } catch(Exception ignored) {}
        rec.setContextType(req.getContextType());
        rec.setStatus("PENDING");
        recommendationRepository.save(rec);

        // Puntos por recomendar — con límite diario FREE
        java.time.LocalDate hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        boolean otorgaPuntos = true;

        if (!me.isActivePremium()) {
            if (!hoy.equals(me.getLastRecommendationDate())) {
                me.setLastRecommendationDate(hoy);
                me.setDailyRecommendationCount(0);
            }
            if (me.getDailyRecommendationCount() >= 3) {
                otorgaPuntos = false;
            } else {
                me.setDailyRecommendationCount(me.getDailyRecommendationCount() + 1);
            }
        }

        int points = 0;
        if (otorgaPuntos) {
            int basePoints = pointConfigService.getPoints(PointAction.RECOMMEND_MOVIE);
            points = me.isActivePremium() ? basePoints * 2 : basePoints;
            me.addAccumulatedPoints(points);
            userRepository.save(me);
            pointTransactionService.registerEarned(
                    me,
                    PointAction.RECOMMEND_MOVIE,
                    points,
                    req.getMovieId(),
                    "Recomendación: " + (rec.getMovieTitle() != null ? rec.getMovieTitle() : "Película #" + req.getMovieId())
            );
        } else {
            userRepository.save(me);
        }

        // Notificar al receptor
        Notification notif = new Notification();
        notif.setUser(receiver);
        notif.setActorId(me.getId());
        notif.setActorName(me.getName());
        notif.setType(NotificationType.NEW_RECOMMENDATION);
        notif.setMovieId(req.getMovieId());
        notif.setMovieTitle(rec.getMovieTitle());
        notif.setMessage(me.getName() + " te recomendó " +
                (rec.getMovieTitle() != null ? rec.getMovieTitle() : "una película"));

        // Inyectar NotificationRepository
        notificationRepository.save(notif);

        return ResponseEntity.ok(Map.of("success", true, "sinPuntos", !otorgaPuntos, "pointsAwarded", points));
    }

    // GET /api/recommendations/received — recomendaciones recibidas
    @GetMapping("/received")
    public ResponseEntity<List<ReceivedRecommendationDto>> recibidas(
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        List<ReceivedRecommendationDto> result = recommendationRepository
                .findByReceiverIdOrderByCreatedAtDesc(me.getId())
                .stream()
                .map(r -> new ReceivedRecommendationDto(
                        r.getId(),
                        r.getSender().getId(),
                        r.getSender().getName(),
                        r.getSender().getEffectiveAvatarUrl(),
                        r.getMovieId(),
                        r.getMovieTitle() != null ? r.getMovieTitle() : resolverTitulo(r.getMovieId()),
                        r.getMoviePosterPath() != null ? r.getMoviePosterPath() : resolverPoster(r.getMovieId()),
                        r.getMovieOverview(),
                        r.getContextType(),
                        r.getStatus(),
                        r.getSeenAt(),
                        r.getRating(),
                        r.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    // POST /api/recommendations/{id}/seen — marcar ya la vi
    @PostMapping("/{id}/seen")
    @Transactional
    public ResponseEntity<?> marcarVista(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        MovieRecommendation rec = recommendationRepository.findByIdAndReceiverId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Recomendación no encontrada"));

        if (rec.getSeenAt() != null)
            return ResponseEntity.badRequest().body(Map.of("error", "Ya marcada como vista"));

        rec.setSeenAt(LocalDateTime.now());
        rec.setStatus("SEEN");
        recommendationRepository.save(rec);

        return ResponseEntity.ok(Map.of("success", true));
    }

    // POST /api/recommendations/{id}/rate — calificar
    @PostMapping("/{id}/rate")
    @Transactional
    public ResponseEntity<?> calificar(@PathVariable Long id,
                                       @RequestBody Map<String, Integer> body,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        MovieRecommendation rec = recommendationRepository.findByIdAndReceiverId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Recomendación no encontrada"));

        if (rec.getSeenAt() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Primero marcala como vista"));

        Integer rating = body.get("rating");
        if (rating == null || rating < 1 || rating > 5)
            return ResponseEntity.badRequest().body(Map.of("error", "Rating inválido (1-5)"));

        rec.setRating(rating.shortValue());
        rec.setRatedAt(LocalDateTime.now());
        rec.setStatus("RATED");
        recommendationRepository.save(rec);

        // Notificar al que recomendó
        notificationService.crearRecomendacionCalificada(
                rec.getSender(),
                me.getName(),
                me.getId(),
                rec.getMovieId(),
                rec.getMovieTitle() != null ? rec.getMovieTitle() : "una película",
                rating
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    // GET /api/movies/{movieId}/usuarios-sin-interaccion
    @GetMapping("/movie/{movieId}/suggested-users")
    public ResponseEntity<List<SuggestedUserDto>> usuariosSugeridos(
            @PathVariable Long movieId,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        List<Object[]> rows = recommendationRepository.findUsersWithoutInteraction(movieId, me.getId(), limit);

        if (rows.isEmpty()) {
            rows = recommendationRepository.findRandomUsers(me.getId(), limit);
        }

        List<SuggestedUserDto> result = rows.stream()
                .map(r -> new SuggestedUserDto(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        (String) r[2]
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    // DELETE /api/recommendations/{id} — eliminar recomendación recibida
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        MovieRecommendation rec = recommendationRepository.findByIdAndReceiverId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Recomendación no encontrada"));
        recommendationRepository.delete(rec);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── helpers ──────────────────────────────────────────────
    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private String resolverTitulo(Long movieId) {
        try {
            var tmdb = movieService.getMovieDetails(movieId);
            return tmdb != null ? tmdb.getTitle() : "Película";
        } catch(Exception e) {
            return "Película";
        }
    }
    private String resolverPoster(Long movieId) {
        try {
            var tmdb = movieService.getMovieDetails(movieId);
            return tmdb != null ? tmdb.getPosterPath() : null;
        } catch(Exception e) {
            return null;
        }
    }
}
