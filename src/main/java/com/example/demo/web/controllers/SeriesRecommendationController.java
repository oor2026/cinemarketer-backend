package com.example.demo.web.controllers;

import com.example.demo.application.dtos.ReceivedRecommendationDto;
import com.example.demo.application.dtos.RecommendationRequest;
import com.example.demo.application.dtos.SuggestedUserDto;
import com.example.demo.application.services.SeriesService;
import com.example.demo.application.services.NotificationService;
import com.example.demo.application.services.PointConfigService;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.notification.Notification;
import com.example.demo.domain.notification.NotificationRepository;
import com.example.demo.domain.notification.NotificationType;
import com.example.demo.domain.recommendation.SeriesRecommendation;
import com.example.demo.domain.recommendation.SeriesRecommendationRepository;
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
@RequestMapping("/api/series-recommendations")
public class SeriesRecommendationController {

    private final SeriesRecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final SeriesService seriesService;
    private final PointConfigService pointConfigService;
    private final PointTransactionService pointTransactionService;

    public SeriesRecommendationController(SeriesRecommendationRepository recommendationRepository,
                                          UserRepository userRepository,
                                          NotificationService notificationService,
                                          NotificationRepository notificationRepository,
                                          SeriesService seriesService,
                                          PointConfigService pointConfigService,
                                          PointTransactionService pointTransactionService) {
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.seriesService = seriesService;
        this.pointConfigService = pointConfigService;
        this.pointTransactionService = pointTransactionService;
    }

    // POST /api/series-recommendations — crear recomendación
    // NOTA: reusa RecommendationRequest tal cual (getMovieId/getReceiverId/
    // getContextType) — es un DTO de transporte genérico, no trae lógica de
    // negocio de película adentro, mismo criterio que ya aplicamos con los
    // DTOs de TMDb. El campo se llama getMovieId() pero acá lo usamos como
    // el id de la serie.
    @PostMapping
    @Transactional
    public ResponseEntity<?> crear(@RequestBody RecommendationRequest req,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        Long seriesId = req.getMovieId();

        if (me.getId().equals(req.getReceiverId()))
            return ResponseEntity.badRequest().body(Map.of("error", "No podés recomendarte a vos mismo"));

        User receiver = userRepository.findById(req.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (recommendationRepository.existsBySenderIdAndReceiverIdAndSeriesId(
                me.getId(), req.getReceiverId(), seriesId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya le recomendaste esta serie"));
        }

        SeriesRecommendation rec = new SeriesRecommendation();
        rec.setSender(me);
        rec.setReceiver(receiver);
        rec.setSeriesId(seriesId);
        try {
            var tmdb = seriesService.getSeriesDetails(seriesId);
            if (tmdb != null) {
                rec.setSeriesTitle(tmdb.getName());
                rec.setSeriesPosterPath(tmdb.getPosterPath());
                rec.setSeriesOverview(tmdb.getOverview());
            }
        } catch (Exception ignored) {}
        rec.setContextType(req.getContextType());
        rec.setStatus("PENDING");
        recommendationRepository.save(rec);

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
            int basePoints = pointConfigService.getPoints(PointAction.RECOMMEND_SERIES);
            points = me.isActivePremium() ? basePoints * 2 : basePoints;
            me.addAccumulatedPoints(points);
            userRepository.save(me);
            pointTransactionService.registerEarned(
                    me,
                    PointAction.RECOMMEND_SERIES,
                    points,
                    seriesId,
                    "Recomendación: " + (rec.getSeriesTitle() != null ? rec.getSeriesTitle() : "Serie #" + seriesId)
            );
        } else {
            userRepository.save(me);
        }

        Notification notif = new Notification();
        notif.setUser(receiver);
        notif.setActorId(me.getId());
        notif.setActorName(me.getName());
        notif.setType(NotificationType.NEW_RECOMMENDATION_SERIES);
        notif.setSeriesId(seriesId);
        notif.setSeriesTitle(rec.getSeriesTitle());
        notif.setMessage(me.getName() + " te recomendó " +
                (rec.getSeriesTitle() != null ? rec.getSeriesTitle() : "una serie"));

        notificationRepository.save(notif);

        return ResponseEntity.ok(Map.of("success", true, "sinPuntos", !otorgaPuntos, "pointsAwarded", points));
    }

    // GET /api/series-recommendations/received
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
                        r.getSeriesId(),
                        r.getSeriesTitle() != null ? r.getSeriesTitle() : resolverTitulo(r.getSeriesId()),
                        r.getSeriesPosterPath() != null ? r.getSeriesPosterPath() : resolverPoster(r.getSeriesId()),
                        r.getSeriesOverview(),
                        r.getContextType(),
                        r.getStatus(),
                        r.getSeenAt(),
                        r.getRating(),
                        r.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    // POST /api/series-recommendations/{id}/seen
    @PostMapping("/{id}/seen")
    @Transactional
    public ResponseEntity<?> marcarVista(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        SeriesRecommendation rec = recommendationRepository.findByIdAndReceiverId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Recomendación no encontrada"));

        if (rec.getSeenAt() != null)
            return ResponseEntity.badRequest().body(Map.of("error", "Ya marcada como vista"));

        rec.setSeenAt(LocalDateTime.now());
        rec.setStatus("SEEN");
        recommendationRepository.save(rec);

        return ResponseEntity.ok(Map.of("success", true));
    }

    // POST /api/series-recommendations/{id}/rate
    @PostMapping("/{id}/rate")
    @Transactional
    public ResponseEntity<?> calificar(@PathVariable Long id,
                                       @RequestBody Map<String, Integer> body,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        SeriesRecommendation rec = recommendationRepository.findByIdAndReceiverId(id, me.getId())
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

        notificationService.crearRecomendacionCalificadaSerie(
                rec.getSender(),
                me.getName(),
                me.getId(),
                rec.getSeriesId(),
                rec.getSeriesTitle() != null ? rec.getSeriesTitle() : "una serie",
                rating
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/series/{seriesId}/suggested-users")
    public ResponseEntity<List<SuggestedUserDto>> usuariosSugeridos(
            @PathVariable Long seriesId,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);

        List<Object[]> rows = recommendationRepository.findUsersWithoutInteraction(seriesId, me.getId(), limit);

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

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User me = getUser(userDetails);
        SeriesRecommendation rec = recommendationRepository.findByIdAndReceiverId(id, me.getId())
                .orElseThrow(() -> new RuntimeException("Recomendación no encontrada"));
        recommendationRepository.delete(rec);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private String resolverTitulo(Long seriesId) {
        try {
            var tmdb = seriesService.getSeriesDetails(seriesId);
            return tmdb != null ? tmdb.getName() : "Serie";
        } catch(Exception e) {
            return "Serie";
        }
    }
    private String resolverPoster(Long seriesId) {
        try {
            var tmdb = seriesService.getSeriesDetails(seriesId);
            return tmdb != null ? tmdb.getPosterPath() : null;
        } catch(Exception e) {
            return null;
        }
    }
}