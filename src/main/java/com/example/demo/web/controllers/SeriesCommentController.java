package com.example.demo.web.controllers;

import com.example.demo.application.dtos.CommentRequest;
import com.example.demo.application.dtos.CommentReportRequest;
import com.example.demo.application.dtos.CommentResponse;
import com.example.demo.application.dtos.CommentReplyResponse;
import com.example.demo.application.services.*;
import com.example.demo.domain.comment.ModerationStatus;
import com.example.demo.domain.comment.ReactionType;
import com.example.demo.domain.series.*;
import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/series-comments")
public class SeriesCommentController {

    private static final int AUTO_HIDE_THRESHOLD     = 5;
    private static final int DAILY_COMMENT_LIMIT     = 10;
    private static final int MERECE_PUNTO_POINTS     = 1;

    private final SeriesCommentRepository         seriesCommentRepository;
    private final SeriesCommentReportRepository   seriesCommentReportRepository;
    private final SeriesCommentReactionRepository seriesCommentReactionRepository;
    private final SeriesCommentReplyRepository    seriesCommentReplyRepository;
    private final UserRepository                  userRepository;
    private final PointConfigService              pointConfigService;
    private final PointTransactionService         pointTransactionService;
    private final SeriesRepository                seriesRepository;
    private final BannedWordService               bannedWordService;
    private final NotificationService              notificationService;
    private final SeriesSpoilerAcceptedRepository  seriesSpoilerAcceptedRepository;
    private final SeriesCommentReplyReportRepository seriesCommentReplyReportRepository;
    private final com.example.demo.application.services.SeriesPersistenceService seriesPersistenceService;

    public SeriesCommentController(SeriesCommentRepository seriesCommentRepository,
                                   SeriesCommentReportRepository seriesCommentReportRepository,
                                   SeriesCommentReactionRepository seriesCommentReactionRepository,
                                   SeriesCommentReplyRepository seriesCommentReplyRepository,
                                   UserRepository userRepository,
                                   PointConfigService pointConfigService,
                                   PointTransactionService pointTransactionService,
                                   SeriesRepository seriesRepository,
                                   BannedWordService bannedWordService,
                                   NotificationService notificationService,
                                   SeriesSpoilerAcceptedRepository seriesSpoilerAcceptedRepository,
                                   SeriesCommentReplyReportRepository seriesCommentReplyReportRepository,
                                   com.example.demo.application.services.SeriesPersistenceService seriesPersistenceService) {
        this.seriesCommentRepository         = seriesCommentRepository;
        this.seriesCommentReportRepository   = seriesCommentReportRepository;
        this.seriesCommentReactionRepository = seriesCommentReactionRepository;
        this.seriesCommentReplyRepository    = seriesCommentReplyRepository;
        this.userRepository                  = userRepository;
        this.pointConfigService              = pointConfigService;
        this.pointTransactionService         = pointTransactionService;
        this.seriesRepository                = seriesRepository;
        this.bannedWordService               = bannedWordService;
        this.notificationService             = notificationService;
        this.seriesSpoilerAcceptedRepository = seriesSpoilerAcceptedRepository;
        this.seriesCommentReplyReportRepository = seriesCommentReplyReportRepository;
        this.seriesPersistenceService = seriesPersistenceService;
    }

    // ── Helper: resolver título de la serie ────────────────────────────────
    private String resolverTituloSerie(Long seriesId) {
        return seriesRepository.findByTmdbId(seriesId)
                .map(Series::getTitle)
                .orElse("Serie #" + seriesId);
    }

    // ── Helper: construir CommentResponse con reacciones ──────────────────────

    private CommentResponse buildResponse(SeriesComment c, Long currentUserId) {
        boolean esPropio = currentUserId != null && c.getUser().getId().equals(currentUserId);
        boolean reportedByMe = !esPropio && currentUserId != null
                && seriesCommentReportRepository.existsByCommentIdAndReporterId(c.getId(), currentUserId);

        CommentResponse r = new CommentResponse(
                c.getId(), c.getUser().getId(), c.getUser().getName(),
                c.getContent(), c.getCreatedAt(), c.getUser().getEffectiveAvatarUrl(),
                reportedByMe, esPropio);

        r.setBancoCount(seriesCommentReactionRepository
                .countByCommentIdAndTypeAndActiveTrueAndReplyIsNull(c.getId(), ReactionType.BANCO));
        r.setMerecePuntoCount(seriesCommentReactionRepository
                .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.MERECE_PUNTO));
        r.setReplyCount(seriesCommentReplyRepository.countVisibleByCommentId(c.getId()));

        if (currentUserId != null) {
            r.setBancadoByMe(seriesCommentReactionRepository
                    .existsByCommentIdAndUserIdAndTypeAndActiveTrueAndNoReply(
                            c.getId(), currentUserId, ReactionType.BANCO));

            seriesCommentReactionRepository
                    .findByCommentIdAndUserIdAndType(c.getId(), currentUserId, ReactionType.MERECE_PUNTO)
                    .ifPresent(reaction -> {
                        r.setMerecePuntoByMe(reaction.isActive());
                        r.setMerecePuntoLocked(reaction.isPointLocked());
                    });
        }
        r.setHasGif(c.getHasGif() != null && c.getHasGif());
        r.setGifUrl(c.getGifUrl());
        r.setSpoiler(c.isSpoiler());
        r.setEditedAt(c.getEditedAt());
        r.setCanEdit(esPropio && c.getEditedAt() == null && c.getCreatedAt().plusMinutes(15).isAfter(LocalDateTime.now()));
        r.setPointsAwarded(c.getPointsAwarded());
        return r;
    }

    // ==========================================================================
    // GET comentarios de una serie — ordenados por banco desc, fecha desc
    // ==========================================================================

    @GetMapping("/series/{seriesId}")
    public ResponseEntity<List<CommentResponse>> getSeriesComments(
            @PathVariable Long seriesId,
            @RequestParam(defaultValue = "false") boolean spoiler,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<SeriesComment> comments = seriesCommentRepository.findVisibleBySeriesIdAndSpoiler(seriesId, spoiler);
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

    @GetMapping("/series/{seriesId}/spoiler-count")
    public ResponseEntity<Map<String, Long>> getSpoilerCount(@PathVariable Long seriesId) {
        long count = seriesCommentRepository.findVisibleBySeriesIdAndSpoiler(seriesId, true).size();
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ==========================================================================
    // POST comentario
    // ==========================================================================

    @PostMapping("/series/{seriesId}")
    @Transactional
    public ResponseEntity<?> addComment(
            @PathVariable Long seriesId,
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

        boolean esDuplicado = false;
        List<SeriesComment> lastVisible = seriesCommentRepository.findLastVisibleByUserIdAndSeriesId(
                user.getId(), seriesId, org.springframework.data.domain.PageRequest.of(0, 1));
        if (!lastVisible.isEmpty()) {
            SeriesComment last = lastVisible.get(0);
            boolean mismoTexto = request.getContent() != null
                    && last.getContent().trim().equalsIgnoreCase(request.getContent().trim());
            boolean mismoGif = request.getGifUrl() != null
                    && request.getGifUrl().equals(last.getGifUrl());
            if (mismoTexto || mismoGif) {
                esDuplicado = true;
            }
        }

        // ── Límite diario de comentarios con puntos — pool compartido con Películas ──
        boolean otorgaPuntos = true;
        if (!user.isActivePremium()) {
            java.time.LocalDate hoy = java.time.LocalDate.now();
            if (!hoy.equals(user.getLastCommentDate())) {
                user.setDailyCommentCount(0);
                user.setLastCommentDate(hoy);
            }
            if (user.getDailyCommentCount() >= DAILY_COMMENT_LIMIT) {
                otorgaPuntos = false;
            } else {
                user.setDailyCommentCount(user.getDailyCommentCount() + 1);
            }
        }

        if (esDuplicado) otorgaPuntos = false;
        int points = otorgaPuntos ? (user.isActivePremium() ? 80 : 40) : 0;

        // Persiste la serie localmente si todavía no existe — antes acá
        // ni siquiera se intentaba, resolverTituloSerie() se resignaba a
        // "Serie #12345" sin pedirle nada a TMDb. Mismo criterio que el
        // resto de las acciones (votos, gustos, recomendaciones, comentarios
        // de película).
        seriesPersistenceService.obtenerOCrearSerie(seriesId);

        SeriesComment comment = new SeriesComment();
        comment.setUser(user);
        comment.setSeriesId(seriesId);
        comment.setContent(request.getContent());
        comment.setPointsAwarded(points);
        comment.setModerationStatus(moderationStatus);
        comment.setSpoiler(request.isSpoiler());
        if (request.getGifUrl() != null && !request.getGifUrl().isBlank()) {
            comment.setGifUrl(request.getGifUrl());
            comment.setHasGif(true);
        }
        seriesCommentRepository.save(comment);

        if (points > 0) {
            user.addAccumulatedPoints(points);
            String seriesTitle = resolverTituloSerie(seriesId);
            pointTransactionService.registerEarned(user, PointAction.COMMENT_SERIES, points,
                    seriesId, "Comentario en serie: " + seriesTitle);
        }
        userRepository.save(user);

        CommentResponse resp = buildResponse(comment, user.getId());
        if (esDuplicado) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "comment", resp,
                            "comentarioDuplicado", true
                    ));
        }
        if (!otorgaPuntos) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "comment", resp,
                            "limiteDiarioAlcanzado", true,
                            "mensaje", "Ya generaste todos tus puntos de hoy 🎬 Podés seguir comentando lo que quieras, pero estos comentarios no sumarán puntos. A las 00hs se renueva tu límite diario y volverás a ganar puntos con tus comentarios."
                    ));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
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

        SeriesComment comment = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        var reactionOpt = seriesCommentReactionRepository
                .findByCommentIdAndUserIdAndTypeAndNoReply(commentId, user.getId(), ReactionType.BANCO);

        boolean nowActive;
        if (reactionOpt.isPresent()) {
            SeriesCommentReaction r = reactionOpt.get();
            r.setActive(!r.isActive());
            seriesCommentReactionRepository.save(r);
            nowActive = r.isActive();
        } else {
            SeriesCommentReaction r = new SeriesCommentReaction();
            r.setComment(comment);
            r.setUser(user);
            r.setType(ReactionType.BANCO);
            r.setActive(true);
            seriesCommentReactionRepository.save(r);
            nowActive = true;

            if (!comment.getUser().getId().equals(user.getId())) {
                String seriesTitle = resolverTituloSerie(comment.getSeriesId());
                notificationService.crearBancoSerie(
                        comment.getUser(), user.getName(),
                        comment.getSeriesId(), seriesTitle, commentId);
            }
        }

        long count = seriesCommentReactionRepository
                .countByCommentIdAndTypeAndActiveTrueAndReplyIsNull(commentId, ReactionType.BANCO);

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

        SeriesCommentReply reply = seriesCommentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        var reactionOpt = seriesCommentReactionRepository
                .findByReplyIdAndUserIdAndType(replyId, user.getId(), ReactionType.BANCO);

        boolean nowActive;
        if (reactionOpt.isPresent()) {
            SeriesCommentReaction r = reactionOpt.get();
            r.setActive(!r.isActive());
            seriesCommentReactionRepository.save(r);
            nowActive = r.isActive();
        } else {
            SeriesCommentReaction r = new SeriesCommentReaction();
            r.setComment(reply.getComment());
            r.setReply(reply);
            r.setUser(user);
            r.setType(ReactionType.BANCO);
            r.setActive(true);
            seriesCommentReactionRepository.save(r);
            nowActive = true;
        }

        long count = seriesCommentReactionRepository
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

        SeriesComment comment = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (comment.getUser().getId().equals(giver.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No podes darte un punto a vos mismo"));
        }

        var reactionOpt = seriesCommentReactionRepository
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

        SeriesCommentReaction r = new SeriesCommentReaction();
        r.setComment(comment);
        r.setUser(giver);
        r.setType(ReactionType.MERECE_PUNTO);
        r.setActive(true);
        r.setPointsAwarded(true);
        r.setPointLocked(true);
        seriesCommentReactionRepository.save(r);

        author.addAccumulatedPoints(MERECE_PUNTO_POINTS);
        userRepository.save(author);

        String seriesTitle = resolverTituloSerie(comment.getSeriesId());

        pointTransactionService.registerEarned(author,
                PointAction.RECEIVE_MERECE_PUNTO, MERECE_PUNTO_POINTS,
                commentId, giver.getName() + " indicó que te ¡Merecés un punto! por tu comentario en " + seriesTitle);

        notificationService.crearMerecePuntoSerie(
                author, giver.getName(),
                comment.getSeriesId(), seriesTitle, commentId);

        long count = seriesCommentReactionRepository
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

        List<SeriesCommentReply> all = seriesCommentReplyRepository.findVisibleByCommentId(commentId);
        long total = all.size();

        int pageSize = 5;
        List<SeriesCommentReply> page = all.stream()
                .skip(offset)
                .limit(pageSize)
                .collect(Collectors.toList());

        List<CommentReplyResponse> response = page.stream().map(rep -> {
            boolean esPropio = currentUserId != null && rep.getUser().getId().equals(currentUserId);
            long bancoCount = seriesCommentReactionRepository
                    .countByReplyIdAndTypeAndActiveTrue(rep.getId(), ReactionType.BANCO);
            boolean bancadoByMe = currentUserId != null && seriesCommentReactionRepository
                    .existsByReplyIdAndUserIdAndTypeAndActiveTrue(rep.getId(), currentUserId, ReactionType.BANCO);

            return new CommentReplyResponse(
                    rep.getId(), rep.getUser().getId(), rep.getUser().getName(),
                    rep.getContent(), rep.getCreatedAt(), rep.getUser().getEffectiveAvatarUrl(),
                    esPropio, bancoCount, bancadoByMe,
                    rep.getModerationStatus().name(),
                    rep.getHasGif() != null && rep.getHasGif(),
                    rep.getGifUrl(),
                    rep.getEditedAt(),
                    esPropio && rep.getEditedAt() == null && rep.getCreatedAt().plusMinutes(15).isAfter(LocalDateTime.now()));
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

        SeriesComment comment = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (bannedWordService.shouldReject(request.getContent())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Tu respuesta no pudo publicarse por no cumplir con nuestras politicas de convivencia.", "rejected", true));
        }

        ModerationStatus moderationStatus = bannedWordService.shouldPendingReview(request.getContent())
                ? ModerationStatus.PENDING_REVIEW : ModerationStatus.APPROVED;

        SeriesCommentReply reply = new SeriesCommentReply();
        reply.setComment(comment);
        reply.setUser(user);
        reply.setContent(request.getContent());
        reply.setModerationStatus(moderationStatus);
        if (request.getGifUrl() != null && !request.getGifUrl().isBlank()) {
            reply.setGifUrl(request.getGifUrl());
            reply.setHasGif(true);
        }
        seriesCommentReplyRepository.save(reply);

        String seriesTitle = resolverTituloSerie(comment.getSeriesId());

        if (!comment.getUser().getId().equals(user.getId())) {
            notificationService.crearReplySerie(
                    comment.getUser(), user.getName(),
                    comment.getSeriesId(), seriesTitle, commentId, reply.getId());
        }

        if (request.getParentReplyId() != null) {
            seriesCommentReplyRepository.findById(request.getParentReplyId()).ifPresent(parentReply -> {
                User parentReplyAuthor = parentReply.getUser();
                boolean esDistintoAlPadre = !parentReplyAuthor.getId().equals(comment.getUser().getId());
                boolean esDistintoAlEmisor = !parentReplyAuthor.getId().equals(user.getId());
                if (esDistintoAlPadre && esDistintoAlEmisor) {
                    notificationService.crearReplySerie(
                            parentReplyAuthor, user.getName(),
                            comment.getSeriesId(), seriesTitle, commentId, reply.getId());
                }
            });
        }

        CommentReplyResponse response = new CommentReplyResponse(
                reply.getId(), user.getId(), user.getName(),
                reply.getContent(), reply.getCreatedAt(),
                user.getEffectiveAvatarUrl(), true, 0L, false,
                reply.getModerationStatus().name(),
                reply.getHasGif() != null && reply.getHasGif(),
                reply.getGifUrl(),
                null,
                true);

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

        SeriesComment comment = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (!comment.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Solo podés ocultar tus propios comentarios"));
        }

        if (comment.getModerationStatus() == ModerationStatus.HIDDEN_BY_USER) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Este comentario ya está oculto"));
        }

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
                String seriesTitle = resolverTituloSerie(comment.getSeriesId());
                pointTransactionService.registerSpent(user, PointAction.COMMENT_SERIES, puntosComentario,
                        comment.getSeriesId(), "Comentario ocultado en serie: " + seriesTitle);
            }
        }

        List<SeriesCommentReaction> merecePuntos = seriesCommentReactionRepository
                .findAllByCommentIdAndType(commentId, ReactionType.MERECE_PUNTO);
        for (SeriesCommentReaction reaction : merecePuntos) {
            if (reaction.isPointsAwarded() && !reaction.isPointLocked()) {
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
        seriesCommentRepository.save(comment);

        return ResponseEntity.ok(Map.of("message", "Tu comentario fue ocultado correctamente."));
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

        SeriesCommentReply reply = seriesCommentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        if (!reply.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Solo podes ocultar tus propias respuestas"));
        }

        if (reply.getModerationStatus() == ModerationStatus.HIDDEN_BY_USER) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Esta respuesta ya esta oculta"));
        }

        reply.setModerationStatus(ModerationStatus.HIDDEN_BY_USER);
        seriesCommentReplyRepository.save(reply);

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

        SeriesComment comment = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (comment.getUser().getId().equals(reporter.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No podes reportar tu propio comentario"));
        }

        if (seriesCommentReportRepository.existsByCommentIdAndReporterId(commentId, reporter.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya reportaste este comentario"));
        }

        if (request.getReason() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El motivo del reporte es obligatorio"));
        }

        SeriesCommentReport report = new SeriesCommentReport();
        report.setComment(comment);
        report.setReporter(reporter);
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        seriesCommentReportRepository.save(report);

        int nuevoConteo = comment.getReportCount() + 1;
        comment.setReportCount(nuevoConteo);
        comment.setAdminReviewed(false);

        if (comment.getModerationStatus() == ModerationStatus.DISMISSED) {
            comment.setModerationStatus(ModerationStatus.APPROVED);
        }

        if (nuevoConteo >= AUTO_HIDE_THRESHOLD && comment.getModerationStatus() == ModerationStatus.APPROVED) {
            comment.setModerationStatus(ModerationStatus.AUTO_HIDDEN);
        }

        seriesCommentRepository.save(comment);

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

        SeriesCommentReply reply = seriesCommentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        if (reply.getUser().getId().equals(reporter.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No podés reportar tu propia respuesta"));
        }

        if (seriesCommentReplyReportRepository.existsByReplyIdAndReporterId(replyId, reporter.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya reportaste esta respuesta"));
        }

        if (request.getReason() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El motivo del reporte es obligatorio"));
        }

        SeriesCommentReplyReport report = new SeriesCommentReplyReport();
        report.setReply(reply);
        report.setReporter(reporter);
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        seriesCommentReplyReportRepository.save(report);

        reply.setReportCount(reply.getReportCount() + 1);
        reply.setAdminReviewed(false);

        if (reply.getModerationStatus() == ModerationStatus.DISMISSED) {
            reply.setModerationStatus(ModerationStatus.APPROVED);
        }

        seriesCommentReplyRepository.save(reply);

        return ResponseEntity.ok(Map.of(
                "message", "Reporte enviado correctamente. Nuestro equipo lo revisará a la brevedad."));
    }

    // ==========================================================================
    // GET / POST spoiler aceptado por serie
    // ==========================================================================

    @GetMapping("/spoiler-accepted/{seriesId}")
    public ResponseEntity<Map<String, Boolean>> getSpoilerAccepted(
            @PathVariable Long seriesId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) return ResponseEntity.ok(Map.of("accepted", false));
        Long userId = resolveUserId(userDetails);

        if (userId == null) return ResponseEntity.ok(Map.of("accepted", false));

        boolean accepted = seriesSpoilerAcceptedRepository.existsByIdUserIdAndIdSeriesId(userId, seriesId);
        return ResponseEntity.ok(Map.of("accepted", accepted));
    }

    @PostMapping("/spoiler-accepted/{seriesId}")
    @Transactional
    public ResponseEntity<Map<String, Boolean>> saveSpoilerAccepted(
            @PathVariable Long seriesId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        SeriesSpoilerAcceptedId id = new SeriesSpoilerAcceptedId(user.getId(), seriesId);
        if (!seriesSpoilerAcceptedRepository.existsById(id)) {
            seriesSpoilerAcceptedRepository.save(new SeriesSpoilerAccepted(id, user));
        }
        return ResponseEntity.ok(Map.of("accepted", true));
    }

    // PATCH /{commentId}/edit — editar comentario propio (hasta 15 min)
    @PatchMapping("/{commentId}/edit")
    @Transactional
    public ResponseEntity<?> editarComentario(
            @PathVariable Long commentId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User me = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        SeriesComment comment = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        if (!comment.getUser().getId().equals(me.getId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo podés editar tus propios comentarios"));
        }

        LocalDateTime limite = comment.getCreatedAt().plusMinutes(15);
        if (LocalDateTime.now().isAfter(limite)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El tiempo para editar este comentario expiró"));
        }

        if (comment.getEditedAt() != null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Este comentario ya fue editado"));
        }

        String nuevoContenido = body.get("content");
        if (nuevoContenido == null || nuevoContenido.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El contenido no puede estar vacío"));
        }

        if (bannedWordService.shouldReject(nuevoContenido.trim())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El comentario contiene palabras no permitidas"));
        }

        comment.setContent(nuevoContenido.trim());
        comment.setEditedAt(LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")));

        if ("true".equals(body.get("removeGif"))) {
            comment.setHasGif(false);
            comment.setGifUrl(null);
        }

        seriesCommentRepository.save(comment);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "content", comment.getContent(),
                "editedAt", comment.getEditedAt().toString(),
                "hasGif", comment.getHasGif() != null && comment.getHasGif()
        ));
    }

    // PATCH /replies/{replyId}/edit — editar respuesta propia (hasta 15 min)
    @PatchMapping("/replies/{replyId}/edit")
    @Transactional
    public ResponseEntity<?> editarRespuesta(
            @PathVariable Long replyId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User me = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        SeriesCommentReply reply = seriesCommentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        if (!reply.getUser().getId().equals(me.getId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo podés editar tus propias respuestas"));
        }

        if (LocalDateTime.now().isAfter(reply.getCreatedAt().plusMinutes(15))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El tiempo para editar esta respuesta expiró"));
        }

        if (reply.getEditedAt() != null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Esta respuesta ya fue editada"));
        }

        String nuevoContenido = body.get("content");
        if (nuevoContenido == null || nuevoContenido.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El contenido no puede estar vacío"));
        }

        if (bannedWordService.shouldReject(nuevoContenido.trim())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El comentario contiene palabras no permitidas"));
        }

        reply.setContent(nuevoContenido.trim());
        reply.setEditedAt(LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")));

        if ("true".equals(body.get("removeGif"))) {
            reply.setHasGif(false);
            reply.setGifUrl(null);
        }

        seriesCommentReplyRepository.save(reply);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "content", reply.getContent(),
                "editedAt", reply.getEditedAt().toString(),
                "hasGif", reply.getHasGif() != null && reply.getHasGif()
        ));
    }

    // ==========================================================================
    // GET un comentario por ID — para consultar si es spoiler desde notificaciones
    // ==========================================================================
    @GetMapping("/{commentId}")
    public ResponseEntity<?> getComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return seriesCommentRepository.findById(commentId)
                .map(c -> ResponseEntity.ok(Map.of("id", c.getId(), "spoiler", c.isSpoiler())))
                .orElse(ResponseEntity.notFound().build());
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