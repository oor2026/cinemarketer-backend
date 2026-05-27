package com.example.demo.web.controllers;

import com.example.demo.application.dtos.CommentRequest;
import com.example.demo.application.dtos.CommentReportRequest;
import com.example.demo.application.dtos.CommentResponse;
import com.example.demo.application.dtos.CommentReplyResponse;
import com.example.demo.application.services.BannedWordService;
import com.example.demo.application.services.NotificationService;
import com.example.demo.application.services.PointConfigService;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.comment.*;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.domain.point.PointAction;
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
public class CommentController {

    private static final int AUTO_HIDE_THRESHOLD          = 5;
    private static final int MAX_HIDDEN_BY_USER_PER_MOVIE = 3;
    private static final int MERECE_PUNTO_POINTS          = 1;

    private final CommentRepository         commentRepository;
    private final CommentReportRepository   commentReportRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final CommentReplyRepository    commentReplyRepository;
    private final UserRepository            userRepository;
    private final PointConfigService        pointConfigService;
    private final PointTransactionService   pointTransactionService;
    private final MovieRepository           movieRepository;
    private final BannedWordService         bannedWordService;
    private final NotificationService       notificationService;

    public CommentController(CommentRepository commentRepository,
                             CommentReportRepository commentReportRepository,
                             CommentReactionRepository commentReactionRepository,
                             CommentReplyRepository commentReplyRepository,
                             UserRepository userRepository,
                             PointConfigService pointConfigService,
                             PointTransactionService pointTransactionService,
                             MovieRepository movieRepository,
                             BannedWordService bannedWordService,
                             NotificationService notificationService) {
        this.commentRepository         = commentRepository;
        this.commentReportRepository   = commentReportRepository;
        this.commentReactionRepository = commentReactionRepository;
        this.commentReplyRepository    = commentReplyRepository;
        this.userRepository            = userRepository;
        this.pointConfigService        = pointConfigService;
        this.pointTransactionService   = pointTransactionService;
        this.movieRepository           = movieRepository;
        this.bannedWordService         = bannedWordService;
        this.notificationService       = notificationService;
    }

    // ── Helper: construir CommentResponse con reacciones ──────────────────────

    private CommentResponse buildResponse(Comment c, Long currentUserId) {
        boolean esPropio = currentUserId != null && c.getUser().getId().equals(currentUserId);
        boolean reportedByMe = !esPropio && currentUserId != null
                && commentReportRepository.existsByCommentIdAndReporterId(c.getId(), currentUserId);

        CommentResponse r = new CommentResponse(
                c.getId(), c.getUser().getId(), c.getUser().getName(),
                c.getContent(), c.getCreatedAt(), c.getUser().getEffectiveAvatarUrl(),
                reportedByMe, esPropio);

        r.setBancoCount(commentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.BANCO));
        r.setMerecePuntoCount(commentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.MERECE_PUNTO));
        r.setReplyCount(commentReplyRepository.countVisibleByCommentId(c.getId()));

        if (currentUserId != null) {
            r.setBancadoByMe(commentReactionRepository
                    .existsByCommentIdAndUserIdAndTypeAndActiveTrueAndNoReply(
                            c.getId(), currentUserId, ReactionType.BANCO));

            commentReactionRepository
                    .findByCommentIdAndUserIdAndType(c.getId(), currentUserId, ReactionType.MERECE_PUNTO)
                    .ifPresent(reaction -> {
                        r.setMerecePuntoByMe(reaction.isActive());
                        r.setMerecePuntoLocked(reaction.isPointLocked());
                    });
        }

        return r;
    }

    // ==========================================================================
    // GET comentarios de una pelicula — ordenados por banco desc, fecha desc
    // ==========================================================================

    @GetMapping("/movies/{movieId}")
    public ResponseEntity<List<CommentResponse>> getMovieComments(
            @PathVariable Long movieId,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<Comment> comments = commentRepository.findVisibleByMovieId(movieId);
        Long currentUserId = resolveUserId(userDetails);

        List<CommentResponse> response = comments.stream()
                .map(c -> buildResponse(c, currentUserId))
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.getBancoCount(), a.getBancoCount());
                    if (cmp != 0) return cmp;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // ==========================================================================
    // POST comentario
    // ==========================================================================

    @PostMapping("/movies/{movieId}")
    @Transactional
    public ResponseEntity<?> addComment(
            @PathVariable Long movieId,
            @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (bannedWordService.shouldReject(request.getContent())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Tu comentario no pudo publicarse por no cumplir con nuestras politicas de convivencia.", "rejected", true));
        }

        ModerationStatus moderationStatus = bannedWordService.shouldPendingReview(request.getContent())
                ? ModerationStatus.PENDING_REVIEW : ModerationStatus.APPROVED;

        commentRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).ifPresent(last -> {
            if (last.getContent().trim().equalsIgnoreCase(request.getContent().trim())) {
                throw new com.example.demo.web.handlers.DuplicateCommentException(
                        "No podés publicar el mismo comentario dos veces seguidas.");
            }
        });

        int points = user.isActivePremium() ? 80 : 40;

        Comment comment = new Comment();
        comment.setUser(user);
        comment.setMovieId(movieId);
        comment.setContent(request.getContent());
        comment.setPointsAwarded(points);
        comment.setModerationStatus(moderationStatus);
        commentRepository.save(comment);

        user.addAccumulatedPoints(points);
        userRepository.save(user);

        String movieTitle = movieRepository.findByTmdbId(movieId)
                .map(Movie::getTitle).orElse("Pelicula #" + movieId);

        pointTransactionService.registerEarned(user, PointAction.COMMENT_MOVIE, points,
                movieId, "Comentario en pelicula: " + movieTitle);

        return ResponseEntity.status(HttpStatus.CREATED).body(buildResponse(comment, user.getId()));
    }

    // ==========================================================================
    // POST Te banco a un comentario (toggle)
    // ==========================================================================

    @PostMapping("/{commentId}/banco")
    @Transactional
    public ResponseEntity<?> toggleBanco(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        var reactionOpt = commentReactionRepository
                .findByCommentIdAndUserIdAndTypeAndNoReply(commentId, user.getId(), ReactionType.BANCO);

        boolean nowActive;
        if (reactionOpt.isPresent()) {
            CommentReaction r = reactionOpt.get();
            r.setActive(!r.isActive());
            commentReactionRepository.save(r);
            nowActive = r.isActive();
        } else {
            CommentReaction r = new CommentReaction();
            r.setComment(comment);
            r.setUser(user);
            r.setType(ReactionType.BANCO);
            r.setActive(true);
            commentReactionRepository.save(r);
            nowActive = true;

            // Notificar al autor (solo si no es el mismo usuario)
            if (!comment.getUser().getId().equals(user.getId())) {
                String movieTitle = movieRepository.findByTmdbId(comment.getMovieId())
                        .map(Movie::getTitle).orElse("una película");
                notificationService.crearBanco(
                        comment.getUser(), user.getName(),
                        comment.getMovieId(), movieTitle, commentId);
            }
        }

        long count = commentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(commentId, ReactionType.BANCO);

        return ResponseEntity.ok(Map.of("active", nowActive, "count", count));
    }

    // ==========================================================================
    // POST Te banco a una respuesta (toggle)
    // ==========================================================================

    @PostMapping("/replies/{replyId}/banco")
    @Transactional
    public ResponseEntity<?> toggleReplyBanco(
            @PathVariable Long replyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        CommentReply reply = commentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        var reactionOpt = commentReactionRepository
                .findByReplyIdAndUserIdAndType(replyId, user.getId(), ReactionType.BANCO);

        boolean nowActive;
        if (reactionOpt.isPresent()) {
            CommentReaction r = reactionOpt.get();
            r.setActive(!r.isActive());
            commentReactionRepository.save(r);
            nowActive = r.isActive();
        } else {
            CommentReaction r = new CommentReaction();
            r.setComment(reply.getComment());
            r.setReply(reply);
            r.setUser(user);
            r.setType(ReactionType.BANCO);
            r.setActive(true);
            commentReactionRepository.save(r);
            nowActive = true;
        }

        long count = commentReactionRepository
                .countByReplyIdAndTypeAndActiveTrue(replyId, ReactionType.BANCO);

        return ResponseEntity.ok(Map.of("active", nowActive, "count", count));
    }

    // ==========================================================================
    // POST ¡Mereces un punto! — irreversible con modal de confirmacion en front
    // ==========================================================================

    @PostMapping("/{commentId}/merece-punto")
    @Transactional
    public ResponseEntity<?> darMerecePunto(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User giver = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (comment.getUser().getId().equals(giver.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No podes darte un punto a vos mismo"));
        }

        // Si ya existe una reaccion previa, rechazar (es irreversible)
        var reactionOpt = commentReactionRepository
                .findByCommentIdAndUserIdAndType(commentId, giver.getId(), ReactionType.MERECE_PUNTO);

        if (reactionOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "alreadyGiven", true,
                            "message", "Ya le diste un punto a este comentario. Esta acción es irreversible."
                    ));
        }

        User author = userRepository.findById(comment.getUser().getId())
                .orElseThrow(() -> new RuntimeException("Autor no encontrado"));

        // Crear reaccion — activa y bloqueada desde el inicio (irreversible)
        CommentReaction r = new CommentReaction();
        r.setComment(comment);
        r.setUser(giver);
        r.setType(ReactionType.MERECE_PUNTO);
        r.setActive(true);
        r.setPointsAwarded(true);
        r.setPointLocked(true); // irreversible desde el momento del otorgamiento
        commentReactionRepository.save(r);

        // Sumar punto acumulado al autor
        author.addAccumulatedPoints(MERECE_PUNTO_POINTS);
        userRepository.save(author);

        pointTransactionService.registerEarned(author,
                PointAction.RECEIVE_MERECE_PUNTO, MERECE_PUNTO_POINTS,
                commentId, "¡Merecés un punto! en comentario #" + commentId);

        // Notificar al autor
        String movieTitle = movieRepository.findByTmdbId(comment.getMovieId())
                .map(Movie::getTitle).orElse("una película");
        notificationService.crearMerecePunto(
                author, giver.getName(),
                comment.getMovieId(), movieTitle, commentId);

        long count = commentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(commentId, ReactionType.MERECE_PUNTO);

        return ResponseEntity.ok(Map.of(
                "active", true,
                "count", count,
                "authorName", author.getName()
        ));
    }

    // ==========================================================================
    // GET respuestas de un comentario (paginadas de a 5)
    // ==========================================================================

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<List<CommentReplyResponse>> getReplies(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long currentUserId = resolveUserId(userDetails);

        List<CommentReply> all = commentReplyRepository.findVisibleByCommentId(commentId);
        long total = all.size();

        int pageSize = 5;
        List<CommentReply> page = all.stream()
                .skip(offset)
                .limit(pageSize)
                .collect(Collectors.toList());

        List<CommentReplyResponse> response = page.stream().map(rep -> {
            boolean esPropio = currentUserId != null && rep.getUser().getId().equals(currentUserId);
            long bancoCount = commentReactionRepository
                    .countByReplyIdAndTypeAndActiveTrue(rep.getId(), ReactionType.BANCO);
            boolean bancadoByMe = currentUserId != null && commentReactionRepository
                    .existsByReplyIdAndUserIdAndTypeAndActiveTrue(rep.getId(), currentUserId, ReactionType.BANCO);

            return new CommentReplyResponse(
                    rep.getId(), rep.getUser().getId(), rep.getUser().getName(),
                    rep.getContent(), rep.getCreatedAt(), rep.getUser().getEffectiveAvatarUrl(),
                    esPropio, bancoCount, bancadoByMe,
                    rep.getModerationStatus().name());
        }).collect(Collectors.toList());

        return ResponseEntity.ok()
                .header("X-Total-Replies", String.valueOf(total))
                .header("X-Has-More", String.valueOf(offset + pageSize < total))
                .body(response);
    }

    // ==========================================================================
    // POST agregar respuesta a un comentario
    // ==========================================================================

    @PostMapping("/{commentId}/replies")
    @Transactional
    public ResponseEntity<?> addReply(
            @PathVariable Long commentId,
            @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (bannedWordService.shouldReject(request.getContent())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Tu respuesta no pudo publicarse por no cumplir con nuestras politicas de convivencia.", "rejected", true));
        }

        ModerationStatus moderationStatus = bannedWordService.shouldPendingReview(request.getContent())
                ? ModerationStatus.PENDING_REVIEW : ModerationStatus.APPROVED;

        commentReplyRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).ifPresent(last -> {
            if (last.getContent().trim().equalsIgnoreCase(request.getContent().trim())) {
                throw new com.example.demo.web.handlers.DuplicateCommentException(
                        "No podés publicar la misma respuesta dos veces seguidas.");
            }
        });

        CommentReply reply = new CommentReply();
        reply.setComment(comment);
        reply.setUser(user);
        reply.setContent(request.getContent());
        reply.setModerationStatus(moderationStatus);
        commentReplyRepository.save(reply);

        // Notificar al autor del comentario original (si no es el mismo usuario)
        if (!comment.getUser().getId().equals(user.getId())) {
            String movieTitle = movieRepository.findByTmdbId(comment.getMovieId())
                    .map(Movie::getTitle).orElse("una película");
            notificationService.crearReply(
                    comment.getUser(), user.getName(),
                    comment.getMovieId(), movieTitle, commentId);
        }

        CommentReplyResponse response = new CommentReplyResponse(
                reply.getId(), user.getId(), user.getName(),
                reply.getContent(), reply.getCreatedAt(),
                user.getEffectiveAvatarUrl(), true, 0L, false,
                reply.getModerationStatus().name());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==========================================================================
    // POST ocultar comentario propio
    // ==========================================================================

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
                    .body(Map.of("error", "Alcanzaste el límite de ocultamientos para esta película.", "limitAlcanzado", true));
        }

        // Revertir puntos por comentar
        int puntosComentario = comment.getPointsAwarded() != null ? comment.getPointsAwarded() : 0;
        if (puntosComentario > 0) {
            boolean restado = false;
            if (user.getAvailablePoints() >= puntosComentario) {
                user.setAvailablePoints(user.getAvailablePoints() - puntosComentario);
                restado = true;
            } else if (user.getAccumulatedPoints() >= puntosComentario) {
                user.setAccumulatedPoints(user.getAccumulatedPoints() - puntosComentario);
                restado = true;
            }
            if (restado) {
                String movieTitle = movieRepository.findByTmdbId(comment.getMovieId())
                        .map(Movie::getTitle).orElse("Pelicula #" + comment.getMovieId());
                pointTransactionService.registerSpent(user, PointAction.COMMENT_MOVIE, puntosComentario,
                        comment.getMovieId(), "Comentario ocultado en pelicula: " + movieTitle);
            }
        }

        // Revertir puntos de cada MERECE_PUNTO recibido
        List<CommentReaction> merecePuntos = commentReactionRepository
                .findAllByCommentIdAndType(commentId, ReactionType.MERECE_PUNTO);

        for (CommentReaction mp : merecePuntos) {
            if (mp.isPointsAwarded()) {
                if (user.getAvailablePoints() >= MERECE_PUNTO_POINTS) {
                    user.setAvailablePoints(user.getAvailablePoints() - MERECE_PUNTO_POINTS);
                    pointTransactionService.registerSpent(user, PointAction.RECEIVE_MERECE_PUNTO,
                            MERECE_PUNTO_POINTS, commentId,
                            "Punto revertido por ocultamiento de comentario #" + commentId);
                } else if (user.getAccumulatedPoints() >= MERECE_PUNTO_POINTS) {
                    user.setAccumulatedPoints(user.getAccumulatedPoints() - MERECE_PUNTO_POINTS);
                    pointTransactionService.registerSpent(user, PointAction.RECEIVE_MERECE_PUNTO,
                            MERECE_PUNTO_POINTS, commentId,
                            "Punto revertido por ocultamiento de comentario #" + commentId);
                }
            }
        }

        userRepository.save(user);

        comment.setModerationStatus(ModerationStatus.HIDDEN_BY_USER);
        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of(
                "message", "Tu comentario fue ocultado correctamente.",
                "ocultamientosRestantes", MAX_HIDDEN_BY_USER_PER_MOVIE - ocultamientos - 1));
    }

    // ==========================================================================
    // POST ocultar respuesta propia
    // ==========================================================================

    @PostMapping("/replies/{replyId}/hide")
    @Transactional
    public ResponseEntity<?> hideReply(
            @PathVariable Long replyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        CommentReply reply = commentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        if (!reply.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Solo podes ocultar tus propias respuestas"));
        }

        if (reply.getModerationStatus() == ModerationStatus.HIDDEN_BY_USER) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Esta respuesta ya esta oculta"));
        }

        // Las respuestas no generan puntos, solo cambiar el status
        reply.setModerationStatus(ModerationStatus.HIDDEN_BY_USER);
        commentReplyRepository.save(reply);

        return ResponseEntity.ok(Map.of("message", "Tu respuesta fue ocultada correctamente."));
    }

    // ==========================================================================
    // POST reportar comentario
    // ==========================================================================

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

        if (nuevoConteo >= AUTO_HIDE_THRESHOLD && comment.getModerationStatus() == ModerationStatus.APPROVED) {
            comment.setModerationStatus(ModerationStatus.AUTO_HIDDEN);
        }

        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of(
                "message", "Reporte enviado correctamente. Nuestro equipo lo revisara a la brevedad.",
                "reportCount", nuevoConteo));
    }

    @PostMapping("/replies/{replyId}/report")
    @Transactional
    public ResponseEntity<?> reportReply(
            @PathVariable Long replyId,
            @RequestBody CommentReportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User reporter = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        CommentReply reply = commentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        if (reply.getUser().getId().equals(reporter.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No podés reportar tu propia respuesta"));
        }

        if (request.getReason() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El motivo del reporte es obligatorio"));
        }

        // Reutilizamos ModerationStatus de la respuesta
        reply.setModerationStatus(ModerationStatus.PENDING_REVIEW);
        commentReplyRepository.save(reply);

        return ResponseEntity.ok(Map.of(
                "message", "Reporte enviado correctamente. Nuestro equipo lo revisará a la brevedad."));
    }

    // ==========================================================================
    // Helper privado
    // ==========================================================================

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null) return null;
        return userRepository.findByEmail(userDetails.getUsername())
                .map(User::getId).orElse(null);
    }
}