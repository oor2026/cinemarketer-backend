package com.example.demo.web.controllers;

import com.example.demo.application.dtos.CommentRequest;
import com.example.demo.application.dtos.CommentReportRequest;
import com.example.demo.application.dtos.CommentResponse;
import com.example.demo.application.services.BannedWordService;
import com.example.demo.application.services.PointConfigService;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.comment.*;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.domain.pointconfig.PointAction;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = "http://localhost:63342")
public class CommentController {

    private static final int AUTO_HIDE_THRESHOLD = 5;
    private static final int MAX_HIDDEN_BY_USER_PER_MOVIE = 3;

    private final CommentRepository commentRepository;
    private final CommentReportRepository commentReportRepository;
    private final UserRepository userRepository;
    private final PointConfigService pointConfigService;
    private final PointTransactionService pointTransactionService;
    private final MovieRepository movieRepository;
    private final BannedWordService bannedWordService;

    public CommentController(CommentRepository commentRepository,
                             CommentReportRepository commentReportRepository,
                             UserRepository userRepository,
                             PointConfigService pointConfigService,
                             PointTransactionService pointTransactionService,
                             MovieRepository movieRepository,
                             BannedWordService bannedWordService) {
        this.commentRepository       = commentRepository;
        this.commentReportRepository = commentReportRepository;
        this.userRepository          = userRepository;
        this.pointConfigService      = pointConfigService;
        this.pointTransactionService = pointTransactionService;
        this.movieRepository         = movieRepository;
        this.bannedWordService       = bannedWordService;
    }

    /**
     * Obtener comentarios visibles de una película
     * GET /api/comments/movies/{movieId}
     */
    @GetMapping("/movies/{movieId}")
    public ResponseEntity<List<CommentResponse>> getMovieComments(
            @PathVariable Long movieId,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<Comment> comments = commentRepository.findVisibleByMovieId(movieId);

        Long userId = null;
        if (userDetails != null) {
            userId = userRepository.findByEmail(userDetails.getUsername())
                    .map(User::getId)
                    .orElse(null);
        }

        final Long currentUserId = userId;

        List<CommentResponse> response = comments.stream()
                .map(c -> {
                    boolean esPropio = currentUserId != null
                            && c.getUser().getId().equals(currentUserId);
                    boolean reportedByMe = !esPropio
                            && currentUserId != null
                            && commentReportRepository.existsByCommentIdAndReporterId(c.getId(), currentUserId);
                    return new CommentResponse(
                            c.getId(),
                            c.getUser().getId(),
                            c.getUser().getName(),
                            c.getContent(),
                            c.getCreatedAt(),
                            c.getUser().getEffectiveAvatarUrl(),
                            reportedByMe,
                            esPropio
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Agregar comentario a una película
     * POST /api/comments/movies/{movieId}
     */
    @PostMapping("/movies/{movieId}")
    @Transactional
    public ResponseEntity<?> addComment(
            @PathVariable Long movieId,
            @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // ── Moderación por lista negra ────────────────────────────────────────
        if (bannedWordService.shouldReject(request.getContent())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "error", "Tu comentario no pudo publicarse por no cumplir con nuestras politicas de convivencia.",
                            "rejected", true
                    ));
        }

        ModerationStatus moderationStatus = bannedWordService.shouldPendingReview(request.getContent())
                ? ModerationStatus.PENDING_REVIEW
                : ModerationStatus.APPROVED;

        // ── Calcular puntos ───────────────────────────────────────────────────
        int basePoints = pointConfigService.getPoints(PointAction.COMMENT_MOVIE);
        int points = user.isActivePremium() ? basePoints * 2 : basePoints;

        // ── Guardar comentario ────────────────────────────────────────────────
        Comment comment = new Comment();
        comment.setUser(user);
        comment.setMovieId(movieId);
        comment.setContent(request.getContent());
        comment.setPointsAwarded(points);
        comment.setToxicityScore(null);
        comment.setModerationStatus(moderationStatus);
        commentRepository.save(comment);

        // ── Sumar puntos ──────────────────────────────────────────────────────
        user.addPoints(points);
        userRepository.save(user);

        String movieTitle = movieRepository.findByTmdbId(movieId)
                .map(Movie::getTitle)
                .orElse("Pelicula #" + movieId);

        pointTransactionService.registerEarned(
                user,
                PointAction.COMMENT_MOVIE,
                points,
                movieId,
                "Comentario en pelicula: " + movieTitle
        );

        // Comentario recién creado: es propio, no reportado
        CommentResponse response = new CommentResponse(
                comment.getId(),
                user.getId(),
                user.getName(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUser().getEffectiveAvatarUrl(),
                false,
                true
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Ocultar un comentario propio
     * POST /api/comments/{commentId}/hide
     */
    @PostMapping("/{commentId}/hide")
    @Transactional
    public ResponseEntity<?> hideComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (!comment.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Solo podés ocultar tus propios comentarios"));
        }

        if (comment.getModerationStatus() == ModerationStatus.HIDDEN_BY_USER) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Este comentario ya está oculto"));
        }

        long ocultamientos = commentRepository.countHiddenByUserAndMovie(user.getId(), comment.getMovieId());
        if (ocultamientos >= MAX_HIDDEN_BY_USER_PER_MOVIE) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "error", "Alcanzaste el límite de ocultamientos para esta película.",
                            "limitAlcanzado", true
                    ));
        }

        comment.setModerationStatus(ModerationStatus.HIDDEN_BY_USER);
        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of(
                "message", "Tu comentario fue ocultado correctamente.",
                "ocultamientosRestantes", MAX_HIDDEN_BY_USER_PER_MOVIE - ocultamientos - 1
        ));
    }

    /**
     * Reportar un comentario
     * POST /api/comments/{commentId}/report
     */
    @PostMapping("/{commentId}/report")
    @Transactional
    public ResponseEntity<?> reportComment(
            @PathVariable Long commentId,
            @RequestBody CommentReportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User reporter = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (comment.getUser().getId().equals(reporter.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No podes reportar tu propio comentario"));
        }

        if (commentReportRepository.existsByCommentIdAndReporterId(commentId, reporter.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya reportaste este comentario"));
        }

        if (request.getReason() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El motivo del reporte es obligatorio"));
        }

        CommentReport report = new CommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        commentReportRepository.save(report);

        int nuevoConteo = comment.getReportCount() + 1;
        comment.setReportCount(nuevoConteo);

        if (nuevoConteo >= AUTO_HIDE_THRESHOLD
                && comment.getModerationStatus() == ModerationStatus.APPROVED) {
            comment.setModerationStatus(ModerationStatus.AUTO_HIDDEN);
        }

        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of(
                "message", "Reporte enviado correctamente. Nuestro equipo lo revisara a la brevedad.",
                "reportCount", nuevoConteo
        ));
    }
}