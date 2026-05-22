package com.example.demo.web.controllers;

import com.example.demo.application.dtos.CommentRequest;
import com.example.demo.application.dtos.CommentReportRequest;
import com.example.demo.application.dtos.CommentResponse;
import com.example.demo.application.dtos.CommentReplyResponse;
import com.example.demo.application.services.BannedWordService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private static final int AUTO_HIDE_THRESHOLD        = 5;
    private static final int MAX_HIDDEN_BY_USER_PER_MOVIE = 3;
    private static final int MERECE_PUNTO_POINTS          = 1;

    private final CommentRepository          commentRepository;
    private final CommentReportRepository    commentReportRepository;
    private final CommentReactionRepository  commentReactionRepository;
    private final CommentReplyRepository     commentReplyRepository;
    private final UserRepository             userRepository;
    private final PointConfigService         pointConfigService;
    private final PointTransactionService    pointTransactionService;
    private final MovieRepository            movieRepository;
    private final BannedWordService          bannedWordService;

    public CommentController(CommentRepository commentRepository,
                             CommentReportRepository commentReportRepository,
                             CommentReactionRepository commentReactionRepository,
                             CommentReplyRepository commentReplyRepository,
                             UserRepository userRepository,
                             PointConfigService pointConfigService,
                             PointTransactionService pointTransactionService,
                             MovieRepository movieRepository,
                             BannedWordService bannedWordService) {
        this.commentRepository       = commentRepository;
        this.commentReportRepository = commentReportRepository;
        this.commentReactionRepository = commentReactionRepository;
        this.commentReplyRepository  = commentReplyRepository;
        this.userRepository          = userRepository;
        this.pointConfigService      = pointConfigService;
        this.pointTransactionService = pointTransactionService;
        this.movieRepository         = movieRepository;
        this.bannedWordService       = bannedWordService;
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

        // Contadores de reacciones
        r.setBancoCount(commentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.BANCO));
        r.setMerecePuntoCount(commentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.MERECE_PUNTO));
        r.setReplyCount(commentReplyRepository.countVisibleByCommentId(c.getId()));

        if (currentUserId != null) {
            r.setBancadoByMe(commentReactionRepository
                    .existsByCommentIdAndUserIdAndTypeAndActiveTrue(
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
                .findByCommentIdAndUserIdAndType(commentId, user.getId(), ReactionType.BANCO);

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

        // Reutilizamos comment_reactions pero con un comment_id negativo como convencion
        // para diferenciar replies: usamos reply.getId() con un tipo especial
        // En realidad: guardamos la reaccion apuntando al comentario padre con referencia al reply
        // Solucion limpia: agregar reply_id nullable a comment_reactions
        // Para MVP: reaccion de reply se guarda con comment_id = reply.getId() * -1 (negativo)
        // NOTA: en produccion refactorizar con campo reply_id

        Long pseudoCommentId = -reply.getId();

        // Creamos un proxy — buscamos por commentId negativo
        var existing = commentReactionRepository
                .findByCommentIdAndUserIdAndType(pseudoCommentId, user.getId(), ReactionType.BANCO);

        boolean nowActive;
        if (existing.isPresent()) {
            CommentReaction r = existing.get();
            r.setActive(!r.isActive());
            commentReactionRepository.save(r);
            nowActive = r.isActive();
        } else {
            // Para evitar FK constraint usamos el comentario padre
            Comment parentComment = reply.getComment();
            CommentReaction r = new CommentReaction();
            r.setComment(parentComment);
            r.setUser(user);
            r.setType(ReactionType.BANCO);
            r.setActive(true);
            // Marcamos con un campo custom que es de reply — guardamos en pointsAwarded=false como flag
            commentReactionRepository.save(r);
            nowActive = true;
        }

        // Contar bancos activos para esta reply
        long count = commentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(reply.getComment().getId(), ReactionType.BANCO);

        return ResponseEntity.ok(Map.of("active", nowActive, "count", count));
    }

    // ==========================================================================
    // POST ¡Mereces un punto! (toggle con logica de puntos)
    // ==========================================================================

    @PostMapping("/{commentId}/merece-punto")
    @Transactional
    public ResponseEntity<?> toggleMerecePunto(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User giver = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        // No podes darte un punto a vos mismo
        if (comment.getUser().getId().equals(giver.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No podes darte un punto a vos mismo"));
        }

        User author = userRepository.findById(comment.getUser().getId())
                .orElseThrow(() -> new RuntimeException("Autor no encontrado"));

        var reactionOpt = commentReactionRepository
                .findByCommentIdAndUserIdAndType(commentId, giver.getId(), ReactionType.MERECE_PUNTO);

        boolean nowActive;
        boolean locked = false;
        String authorName = author.getName();

        if (reactionOpt.isPresent()) {
            CommentReaction r = reactionOpt.get();

            // Si el punto ya esta bloqueado (paso a disponible), no se puede retirar
            if (r.isPointLocked()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "locked", true,
                                "message", "Este punto ya está disponible para " + authorName + ". No es posible retirarlo."
                        ));
            }

            // Toggle normal
            r.setActive(!r.isActive());
            commentReactionRepository.save(r);
            nowActive = r.isActive();

            // Si se desactiva: restar punto acumulado al autor
            if (!nowActive && r.isPointsAwarded()) {
                author.addAccumulatedPoints(-MERECE_PUNTO_POINTS);
                userRepository.save(author);
                pointTransactionService.registerEarned(author,
                        PointAction.REVERT_MERECE_PUNTO, -MERECE_PUNTO_POINTS,
                        commentId, "Retiro de ¡Merecés un punto! en comentario #" + commentId);
            }

            // Si se reactiva: solo sumar si pointsAwarded es false (antifraude)
            if (nowActive && !r.isPointsAwarded()) {
                r.setPointsAwarded(true);
                commentReactionRepository.save(r);
                author.addAccumulatedPoints(MERECE_PUNTO_POINTS);
                userRepository.save(author);
                pointTransactionService.registerEarned(author,
                        PointAction.RECEIVE_MERECE_PUNTO, MERECE_PUNTO_POINTS,
                        commentId, "¡Merecés un punto! en comentario #" + commentId);
            }

        } else {
            // Primera vez — crear reaccion y otorgar punto
            CommentReaction r = new CommentReaction();
            r.setComment(comment);
            r.setUser(giver);
            r.setType(ReactionType.MERECE_PUNTO);
            r.setActive(true);
            r.setPointsAwarded(true);
            commentReactionRepository.save(r);
            nowActive = true;

            author.addAccumulatedPoints(MERECE_PUNTO_POINTS);
            userRepository.save(author);
            pointTransactionService.registerEarned(author,
                    PointAction.RECEIVE_MERECE_PUNTO, MERECE_PUNTO_POINTS,
                    commentId, "¡Merecés un punto! en comentario #" + commentId);
        }

        long count = commentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(commentId, ReactionType.MERECE_PUNTO);

        return ResponseEntity.ok(Map.of(
                "active", nowActive,
                "count", count,
                "locked", locked,
                "authorName", authorName
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

        // Paginacion de a 5
        int pageSize = 5;
        List<CommentReply> page = all.stream()
                .skip(offset)
                .limit(pageSize)
                .collect(Collectors.toList());

        List<CommentReplyResponse> response = page.stream().map(r -> {
            boolean esPropio = currentUserId != null && r.getUser().getId().equals(currentUserId);
            long bancoCount = commentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(r.getId(), ReactionType.BANCO);
            boolean bancadoByMe = currentUserId != null && commentReactionRepository
                    .existsByCommentIdAndUserIdAndTypeAndActiveTrue(r.getId(), currentUserId, ReactionType.BANCO);

            return new CommentReplyResponse(
                    r.getId(), r.getUser().getId(), r.getUser().getName(),
                    r.getContent(), r.getCreatedAt(), r.getUser().getEffectiveAvatarUrl(),
                    esPropio, bancoCount, bancadoByMe);
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

        // Moderacion
        if (bannedWordService.shouldReject(request.getContent())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Tu respuesta no pudo publicarse por no cumplir con nuestras politicas de convivencia.", "rejected", true));
        }

        ModerationStatus moderationStatus = bannedWordService.shouldPendingReview(request.getContent())
                ? ModerationStatus.PENDING_REVIEW : ModerationStatus.APPROVED;

        // Antispam
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

        // Sin puntos por responder — los puntos se ganan recibiendo Te banco
        CommentReplyResponse response = new CommentReplyResponse(
                reply.getId(), user.getId(), user.getName(),
                reply.getContent(), reply.getCreatedAt(),
                user.getEffectiveAvatarUrl(), true, 0L, false);

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

        comment.setModerationStatus(ModerationStatus.HIDDEN_BY_USER);
        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of(
                "message", "Tu comentario fue ocultado correctamente.",
                "ocultamientosRestantes", MAX_HIDDEN_BY_USER_PER_MOVIE - ocultamientos - 1));
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

    // ==========================================================================
    // Helper privado
    // ==========================================================================

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null) return null;
        return userRepository.findByEmail(userDetails.getUsername())
                .map(User::getId).orElse(null);
    }
}