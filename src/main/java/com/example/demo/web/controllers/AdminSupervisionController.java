package com.example.demo.web.controllers;

import com.example.demo.application.dtos.CommentModerationDto;
import com.example.demo.application.dtos.CommentRemoveRequest;
import com.example.demo.application.services.EmailService;
import com.example.demo.domain.comment.*;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.domain.series.Series;
import com.example.demo.domain.series.SeriesRepository;
import com.example.demo.domain.series.SeriesComment;
import com.example.demo.domain.series.SeriesCommentReply;
import com.example.demo.domain.series.SeriesCommentReaction;
import com.example.demo.domain.series.SeriesCommentReport;
import com.example.demo.domain.series.SeriesCommentReplyReport;
import com.example.demo.domain.series.SeriesCommentRepository;
import com.example.demo.domain.series.SeriesCommentReplyRepository;
import com.example.demo.domain.series.SeriesCommentReactionRepository;
import com.example.demo.domain.series.SeriesCommentReportRepository;
import com.example.demo.domain.series.SeriesCommentReplyReportRepository;
import com.example.demo.application.services.SeriesService;
import com.example.demo.application.dtos.SeriesCommentModerationDto;
import com.example.demo.domain.publication.PublicationComment;
import com.example.demo.domain.publication.PublicationCommentModerationStatus;
import com.example.demo.domain.publication.PublicationCommentRepository;
import com.example.demo.domain.publication.PublicationReport;
import com.example.demo.domain.publication.PublicationReportRepository;
import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.pointtransaction.PointTransaction;
import com.example.demo.domain.pointtransaction.PointTransactionRepository;
import com.example.demo.domain.pointtransaction.PointTransactionType;
import com.example.demo.domain.support.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/supervision")
@RequiredArgsConstructor
public class AdminSupervisionController {

    private static final int MERECE_PUNTO_POINTS = 1;

    private final CommentRepository           commentRepository;
    private final CommentReportRepository     commentReportRepository;
    private final CommentReplyRepository      commentReplyRepository;
    private final CommentReactionRepository   commentReactionRepository;
    private final PublicationCommentRepository publicationCommentRepository;
    private final PublicationReportRepository  publicationReportRepository;
    private final CommentReplyReportRepository commentReplyReportRepository;
    private final com.example.demo.infrastructure.external.tmdb.TmdbService tmdbService;
    private final SupportTicketRepository     supportTicketRepository;
    private final SupportMessageRepository    supportMessageRepository;
    private final PointTransactionRepository  pointTransactionRepository;
    private final UserRepository              userRepository;
    private final MovieRepository             movieRepository;
    private final EmailService                emailService;
    private final com.example.demo.application.services.NotificationService notificationService;
    private final SeriesCommentRepository            seriesCommentRepository;
    private final SeriesCommentReplyRepository       seriesCommentReplyRepository;
    private final SeriesCommentReactionRepository    seriesCommentReactionRepository;
    private final SeriesCommentReportRepository      seriesCommentReportRepository;
    private final SeriesCommentReplyReportRepository seriesCommentReplyReportRepository;
    private final SeriesRepository                   seriesRepository;
    private final SeriesService                      seriesService;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Long>> getStats() {
        long pendientes = commentRepository.findPending().size()
                + commentReplyRepository.findPending().size()
                + publicationCommentRepository.findPending().size()
                + seriesCommentRepository.findPending().size()
                + seriesCommentReplyRepository.findPending().size();
        long enRevision = commentRepository.findInReview().size()
                + commentReplyRepository.findInReview().size()
                + publicationCommentRepository.findInReview().size()
                + seriesCommentRepository.findInReview().size()
                + seriesCommentReplyRepository.findInReview().size();
        long resueltos  = commentRepository.findResolved().size()
                + commentReplyRepository.findResolved().size()
                + publicationCommentRepository.findResolved().size()
                + seriesCommentRepository.findResolved().size()
                + seriesCommentReplyRepository.findResolved().size();

        return ResponseEntity.ok(Map.of(
                "pendientes",  pendientes,
                "enRevision",  enRevision,
                "resueltos",   resueltos
        ));
    }

    // Pendientes: comentarios + respuestas + comentarios de publicaciones que el admin no revisó
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getPending() {
        List<Map<String, Object>> todos = new java.util.ArrayList<>();
        commentRepository.findPending().forEach(c -> todos.add(wrap("MOVIE", toDto(c))));
        commentReplyRepository.findPending().forEach(r -> todos.add(wrap("MOVIE", toDtoFromReply(r))));
        publicationCommentRepository.findPending().forEach(c -> todos.add(wrap("PUBLICATION", toDtoFromPublicationComment(c))));
        seriesCommentRepository.findPending().forEach(c -> todos.add(wrap("SERIES", toDtoFromSeries(c))));
        seriesCommentReplyRepository.findPending().forEach(r -> todos.add(wrap("SERIES", toDtoFromSeriesReply(r))));
        todos.sort((a, b) -> extractCreatedAt(b).compareTo(extractCreatedAt(a)));
        return ResponseEntity.ok(todos);
    }

    // En revisión: comentarios + respuestas + comentarios de publicaciones que el admin ya vio
    @GetMapping("/in-review")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getInReview() {
        List<Map<String, Object>> todos = new java.util.ArrayList<>();
        commentRepository.findInReview().forEach(c -> todos.add(wrap("MOVIE", toDto(c))));
        commentReplyRepository.findInReview().forEach(r -> todos.add(wrap("MOVIE", toDtoFromReply(r))));
        publicationCommentRepository.findInReview().forEach(c -> todos.add(wrap("PUBLICATION", toDtoFromPublicationComment(c))));
        seriesCommentRepository.findInReview().forEach(c -> todos.add(wrap("SERIES", toDtoFromSeries(c))));
        seriesCommentReplyRepository.findInReview().forEach(r -> todos.add(wrap("SERIES", toDtoFromSeriesReply(r))));
        todos.sort((a, b) -> extractCreatedAt(b).compareTo(extractCreatedAt(a)));
        return ResponseEntity.ok(todos);
    }

    // Resueltos: eliminados + desestimados de comentarios, respuestas y comentarios de publicaciones
    @GetMapping("/resolved")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getResolved() {
        List<Map<String, Object>> todos = new java.util.ArrayList<>();
        commentRepository.findResolved().forEach(c -> todos.add(wrap("MOVIE", toDto(c))));
        commentReplyRepository.findResolved().forEach(r -> todos.add(wrap("MOVIE", toDtoFromReply(r))));
        publicationCommentRepository.findResolved().forEach(c -> todos.add(wrap("PUBLICATION", toDtoFromPublicationComment(c))));
        seriesCommentRepository.findResolved().forEach(c -> todos.add(wrap("SERIES", toDtoFromSeries(c))));
        seriesCommentReplyRepository.findResolved().forEach(r -> todos.add(wrap("SERIES", toDtoFromSeriesReply(r))));
        todos.sort((a, b) -> extractCreatedAt(b).compareTo(extractCreatedAt(a)));
        return ResponseEntity.ok(todos);
    }

    // Envuelve cada item con su tipo de origen, para que el admin sepa a qué
    // endpoint pegarle (/x/remove vs /publications/x/remove) sin acoplar los DTOs
    private Map<String, Object> wrap(String sourceType, Object dto) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("sourceType", sourceType);
        m.put("data", dto);
        return m;
    }

    @SuppressWarnings("unchecked")
    private LocalDateTime extractCreatedAt(Map<String, Object> wrapped) {
        Object dto = wrapped.get("data");
        if (dto instanceof CommentModerationDto d) return d.getCreatedAt();
        if (dto instanceof com.example.demo.application.dtos.PublicationCommentModerationDto d) return d.getCreatedAt();
        if (dto instanceof SeriesCommentModerationDto d) return d.getCreatedAt();
        return LocalDateTime.MIN;
    }

    // Marcar como revisado (al cerrar el modal de detalle)
    @PostMapping("/{commentId}/mark-reviewed")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> markReviewed(@PathVariable Long commentId,
                                          @RequestParam(defaultValue = "false") boolean isReply) {
        if (isReply) {
            commentReplyRepository.findById(commentId).ifPresent(r -> {
                r.setAdminReviewed(true);
                commentReplyRepository.save(r);
            });
        } else {
            commentRepository.findById(commentId).ifPresent(c -> {
                c.setAdminReviewed(true);
                commentRepository.save(c);
            });
        }
        return ResponseEntity.ok(Map.of("message", "Marcado como revisado"));
    }

    @PostMapping("/{commentId}/remove")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> removeComment(
            @PathVariable Long commentId,
            @RequestBody CommentRemoveRequest request) {

        if (request.getReason() == null || request.getReason().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El motivo de eliminacion es obligatorio"));
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        User autor = comment.getUser();
        String contenido = displayContent(comment.getContent(), comment.getHasGif());

        String movieTitle = resolveMovieTitle(comment.getMovieId());

        // Revertir puntos por comentar
        int puntosComentario = comment.getPointsAwarded() != null ? comment.getPointsAwarded() : 0;
        boolean comentarioRestado = revertirPuntos(autor, puntosComentario);
        if (comentarioRestado && puntosComentario > 0) {
            PointTransaction pt = new PointTransaction();
            pt.setUser(autor);
            pt.setType(PointTransactionType.SPENT);
            pt.setAction(PointAction.COMMENT_MOVIE);
            pt.setPoints(puntosComentario);
            pt.setReferenceId(comment.getMovieId());
            pt.setReferenceTitle("Comentario eliminado por moderacion en pelicula: " + movieTitle);
            pointTransactionRepository.save(pt);
        }

        // Revertir puntos de cada MERECE_PUNTO recibido
        List<CommentReaction> merecePuntos = commentReactionRepository
                .findAllByCommentIdAndType(commentId, ReactionType.MERECE_PUNTO);

        for (CommentReaction mp : merecePuntos) {
            if (mp.isPointsAwarded()) {
                boolean mpRestado = revertirPuntos(autor, MERECE_PUNTO_POINTS);
                if (mpRestado) {
                    PointTransaction ptMp = new PointTransaction();
                    ptMp.setUser(autor);
                    ptMp.setType(PointTransactionType.SPENT);
                    ptMp.setAction(PointAction.RECEIVE_MERECE_PUNTO);
                    ptMp.setPoints(MERECE_PUNTO_POINTS);
                    ptMp.setReferenceId(commentId);
                    ptMp.setReferenceTitle("Punto revertido por eliminacion de comentario #" + commentId);
                    pointTransactionRepository.save(ptMp);
                }
            }
        }

        userRepository.save(autor);

        // Marcar como eliminado
        comment.setModerationStatus(ModerationStatus.REMOVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        commentRepository.save(comment);

        // Ticket + mensaje interno
        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUser(autor);
            ticket.setSubject("Tu comentario fue eliminado");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            String mensajeTicket = String.format(
                    "Tu comentario fue eliminado por no cumplir con nuestras politicas de convivencia.\n\n" +
                            "Pelicula: \"%s\"\n\n" +
                            "Comentario eliminado:\n\"%s\"\n\nMotivo:\n%s\n\n" +
                            "Si tenes consultas al respecto, podes responder este mensaje.",
                    movieTitle,
                    contenido.length() > 200 ? contenido.substring(0, 200) + "..." : contenido,
                    request.getReason());

            SupportMessage mensaje = new SupportMessage();
            mensaje.setTicket(savedTicket);
            mensaje.setSenderType(SenderType.ADMIN);
            mensaje.setSenderName("Cinemarketer");
            mensaje.setContent(mensajeTicket);
            mensaje.setReadByAdmin(true);
            mensaje.setReadByUser(false);
            supportMessageRepository.save(mensaje);
        } catch (Exception ignored) {}

        try {
            emailService.sendCommentRemovedEmail(autor.getEmail(), autor.getName(),
                    contenido, request.getReason());
        } catch (Exception ignored) {}

        try {
            notificationService.crearComentarioEliminado(autor, comment.getMovieId(), movieTitle, contenido, "comentario");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("message", "Comentario eliminado y usuario notificado"));
    }

    @PostMapping("/{commentId}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> restoreComment(@PathVariable Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        User autor = comment.getUser();

        // Re-otorgar puntos por comentar
        int puntosComentario = comment.getPointsAwarded() != null ? comment.getPointsAwarded() : 0;
        if (puntosComentario > 0) {
            autor.addAccumulatedPoints(puntosComentario);

            String movieTitle = resolveMovieTitle(comment.getMovieId());

            PointTransaction pt = new PointTransaction();
            pt.setUser(autor);
            pt.setType(PointTransactionType.EARNED);
            pt.setAction(PointAction.COMMENT_MOVIE);
            pt.setPoints(puntosComentario);
            pt.setReferenceId(comment.getMovieId());
            pt.setReferenceTitle("Comentario restaurado en pelicula: " + movieTitle);
            pointTransactionRepository.save(pt);
        }

        // Re-otorgar puntos de cada MERECE_PUNTO
        List<CommentReaction> merecePuntos = commentReactionRepository
                .findAllByCommentIdAndType(commentId, ReactionType.MERECE_PUNTO);

        for (CommentReaction mp : merecePuntos) {
            if (mp.isPointsAwarded()) {
                autor.addAccumulatedPoints(MERECE_PUNTO_POINTS);

                PointTransaction ptMp = new PointTransaction();
                ptMp.setUser(autor);
                ptMp.setType(PointTransactionType.EARNED);
                ptMp.setAction(PointAction.RECEIVE_MERECE_PUNTO);
                ptMp.setPoints(MERECE_PUNTO_POINTS);
                ptMp.setReferenceId(commentId);
                ptMp.setReferenceTitle("Merecio un punto restaurado en comentario #" + commentId);
                pointTransactionRepository.save(ptMp);
            }
        }

        userRepository.save(autor);

        comment.setModerationStatus(ModerationStatus.APPROVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of("message", "Comentario restaurado y puntos re-otorgados"));
    }

    @PostMapping("/{commentId}/dismiss")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> dismissComment(@PathVariable Long commentId,
                                            @RequestParam(defaultValue = "false") boolean isReply) {
        if (isReply) {
            CommentReply reply = commentReplyRepository.findById(commentId)
                    .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

            List<CommentReplyReport> reports = commentReplyReportRepository.findByReplyIdOrderByCreatedAtDesc(commentId);
            for (CommentReplyReport report : reports) {
                User reporter = report.getReporter();
                try {
                    SupportTicket ticket = new SupportTicket();
                    ticket.setUser(reporter);
                    ticket.setSubject("Revisamos tu reporte");
                    ticket.setStatus(TicketStatus.OPEN);
                    SupportTicket savedTicket = supportTicketRepository.save(ticket);

                    String movieTitle = resolveMovieTitle(reply.getComment().getMovieId());
                    String contenidoReplyDisplay = displayContent(reply.getContent(), reply.getHasGif());
                    String contenidoCorto = contenidoReplyDisplay.length() > 200
                            ? contenidoReplyDisplay.substring(0, 200) + "..." : contenidoReplyDisplay;

                    String mensaje = "Hemos revisado la respuesta que reportaste en la película \"" + movieTitle + "\":\n\n" +
                            "\"" + contenidoCorto + "\"\n\n" +
                            "La misma no viola ninguno de nuestros reglamentos, política de privacidad ni normas de convivencia. " +
                            "Esperamos que sigas ayudándonos a sanear el contenido con cualquier otra respuesta que creas que " +
                            "viola alguna regla de nuestra comunidad. Saludos.";

                    SupportMessage msg = new SupportMessage();
                    msg.setTicket(savedTicket);
                    msg.setSenderType(SenderType.ADMIN);
                    msg.setSenderName("Cinemarketer");
                    msg.setContent(mensaje);
                    msg.setReadByAdmin(true);
                    msg.setReadByUser(false);
                    supportMessageRepository.save(msg);
                } catch (Exception ignored) {}
            }
            commentReplyReportRepository.deleteByReplyId(commentId);

            reply.setReportCount(0);
            reply.setModerationStatus(ModerationStatus.DISMISSED);
            reply.setAdminReviewed(true);
            reply.setModerationReviewedAt(LocalDateTime.now());
            commentReplyRepository.save(reply);
        } else {
            Comment comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

            List<CommentReport> reports = commentReportRepository.findByCommentIdOrderByCreatedAtDesc(commentId);
            for (CommentReport report : reports) {
                User reporter = report.getReporter();
                try {
                    SupportTicket ticket = new SupportTicket();
                    ticket.setUser(reporter);
                    ticket.setSubject("Revisamos tu reporte");
                    ticket.setStatus(TicketStatus.OPEN);
                    SupportTicket savedTicket = supportTicketRepository.save(ticket);

                    String movieTitle = resolveMovieTitle(comment.getMovieId());
                    String contenidoDisplay = displayContent(comment.getContent(), comment.getHasGif());
                    String contenidoCorto = contenidoDisplay.length() > 200
                            ? contenidoDisplay.substring(0, 200) + "..." : contenidoDisplay;

                    String mensaje = "Hemos revisado el comentario que reportaste en la película \"" + movieTitle + "\":\n\n" +
                            "\"" + contenidoCorto + "\"\n\n" +
                            "El mismo no viola ninguno de nuestros reglamentos, política de privacidad ni normas de convivencia. " +
                            "Esperamos que sigas ayudándonos a sanear el contenido con cualquier otro comentario que creas que " +
                            "viola alguna regla de nuestra comunidad. Saludos.";

                    SupportMessage msg = new SupportMessage();
                    msg.setTicket(savedTicket);
                    msg.setSenderType(SenderType.ADMIN);
                    msg.setSenderName("Cinemarketer");
                    msg.setContent(mensaje);
                    msg.setReadByAdmin(true);
                    msg.setReadByUser(false);
                    supportMessageRepository.save(msg);
                } catch (Exception ignored) {}
            }
            commentReportRepository.deleteByCommentId(commentId);

            comment.setReportCount(0);
            comment.setModerationStatus(ModerationStatus.DISMISSED);
            comment.setAdminReviewed(true);
            comment.setModerationReviewedAt(LocalDateTime.now());
            commentRepository.save(comment);
        }
        return ResponseEntity.ok(Map.of("message", "Reporte desestimado correctamente"));
    }

    @PostMapping("/replies/{replyId}/remove")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> removeReply(
            @PathVariable Long replyId,
            @RequestBody CommentRemoveRequest request) {

        if (request.getReason() == null || request.getReason().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El motivo de eliminacion es obligatorio"));
        }

        CommentReply reply = commentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        User autor = reply.getUser();
        Comment comentarioPadre = reply.getComment();
        String contenidoReply = displayContent(reply.getContent(), reply.getHasGif());
        String contenidoPadre = displayContent(comentarioPadre.getContent(), comentarioPadre.getHasGif());

        String movieTitle = resolveMovieTitle(comentarioPadre.getMovieId());

        reply.setModerationStatus(ModerationStatus.REMOVED);
        reply.setModerationReviewedAt(LocalDateTime.now());
        commentReplyRepository.save(reply);

        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUser(autor);
            ticket.setSubject("Tu respuesta fue eliminada");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            String extractoReply  = contenidoReply.length()  > 100 ? contenidoReply.substring(0, 100)  + "..." : contenidoReply;
            String extractoPadre  = contenidoPadre.length()  > 100 ? contenidoPadre.substring(0, 100)  + "..." : contenidoPadre;

            String mensajeTicket = String.format(
                    "Tu respuesta \"%s\" sobre el comentario \"%s\" en la pelicula \"%s\" " +
                            "fue eliminada por no cumplir con nuestras normas de convivencia.\n\n" +
                            "Motivo:\n%s\n\n" +
                            "Si tenes consultas al respecto, podes responder este mensaje.",
                    extractoReply, extractoPadre, movieTitle, request.getReason());

            SupportMessage mensaje = new SupportMessage();
            mensaje.setTicket(savedTicket);
            mensaje.setSenderType(SenderType.ADMIN);
            mensaje.setSenderName("Cinemarketer");
            mensaje.setContent(mensajeTicket);
            mensaje.setReadByAdmin(true);
            mensaje.setReadByUser(false);
            supportMessageRepository.save(mensaje);
        } catch (Exception ignored) {}

        try {
            notificationService.crearComentarioEliminado(autor, comentarioPadre.getMovieId(), movieTitle, contenidoReply, "respuesta");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("message", "Respuesta eliminada y usuario notificado"));
    }

    @PostMapping("/replies/{replyId}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> restoreReply(@PathVariable Long replyId) {
        CommentReply reply = commentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        reply.setModerationStatus(ModerationStatus.APPROVED);
        commentReplyRepository.save(reply);

        return ResponseEntity.ok(Map.of("message", "Respuesta restaurada correctamente"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // COMENTARIOS DE SERIES — calco exacto del bloque de Película de arriba
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/series/{commentId}/mark-reviewed")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> markReviewedSeries(@PathVariable Long commentId,
                                                @RequestParam(defaultValue = "false") boolean isReply) {
        if (isReply) {
            seriesCommentReplyRepository.findById(commentId).ifPresent(r -> {
                r.setAdminReviewed(true);
                seriesCommentReplyRepository.save(r);
            });
        } else {
            seriesCommentRepository.findById(commentId).ifPresent(c -> {
                c.setAdminReviewed(true);
                seriesCommentRepository.save(c);
            });
        }
        return ResponseEntity.ok(Map.of("message", "Marcado como revisado"));
    }

    @PostMapping("/series/{commentId}/remove")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> removeSeriesComment(
            @PathVariable Long commentId,
            @RequestBody CommentRemoveRequest request) {

        if (request.getReason() == null || request.getReason().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El motivo de eliminacion es obligatorio"));
        }

        SeriesComment comment = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        User autor = comment.getUser();
        String contenido = displayContent(comment.getContent(), comment.getHasGif());
        String seriesTitle = resolveSeriesTitle(comment.getSeriesId());

        int puntosComentario = comment.getPointsAwarded() != null ? comment.getPointsAwarded() : 0;
        boolean comentarioRestado = revertirPuntos(autor, puntosComentario);
        if (comentarioRestado && puntosComentario > 0) {
            PointTransaction pt = new PointTransaction();
            pt.setUser(autor);
            pt.setType(PointTransactionType.SPENT);
            pt.setAction(PointAction.COMMENT_SERIES);
            pt.setPoints(puntosComentario);
            pt.setReferenceId(comment.getSeriesId());
            pt.setReferenceTitle("Comentario eliminado por moderacion en serie: " + seriesTitle);
            pointTransactionRepository.save(pt);
        }

        List<SeriesCommentReaction> merecePuntos = seriesCommentReactionRepository
                .findAllByCommentIdAndType(commentId, ReactionType.MERECE_PUNTO);

        for (SeriesCommentReaction mp : merecePuntos) {
            if (mp.isPointsAwarded()) {
                boolean mpRestado = revertirPuntos(autor, MERECE_PUNTO_POINTS);
                if (mpRestado) {
                    PointTransaction ptMp = new PointTransaction();
                    ptMp.setUser(autor);
                    ptMp.setType(PointTransactionType.SPENT);
                    ptMp.setAction(PointAction.RECEIVE_MERECE_PUNTO);
                    ptMp.setPoints(MERECE_PUNTO_POINTS);
                    ptMp.setReferenceId(commentId);
                    ptMp.setReferenceTitle("Punto revertido por eliminacion de comentario #" + commentId);
                    pointTransactionRepository.save(ptMp);
                }
            }
        }

        userRepository.save(autor);

        comment.setModerationStatus(ModerationStatus.REMOVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        seriesCommentRepository.save(comment);

        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUser(autor);
            ticket.setSubject("Tu comentario fue eliminado");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            String mensajeTicket = String.format(
                    "Tu comentario fue eliminado por no cumplir con nuestras politicas de convivencia.\n\n" +
                            "Serie: \"%s\"\n\n" +
                            "Comentario eliminado:\n\"%s\"\n\nMotivo:\n%s\n\n" +
                            "Si tenes consultas al respecto, podes responder este mensaje.",
                    seriesTitle,
                    contenido.length() > 200 ? contenido.substring(0, 200) + "..." : contenido,
                    request.getReason());

            SupportMessage mensaje = new SupportMessage();
            mensaje.setTicket(savedTicket);
            mensaje.setSenderType(SenderType.ADMIN);
            mensaje.setSenderName("Cinemarketer");
            mensaje.setContent(mensajeTicket);
            mensaje.setReadByAdmin(true);
            mensaje.setReadByUser(false);
            supportMessageRepository.save(mensaje);
        } catch (Exception ignored) {}

        try {
            emailService.sendCommentRemovedEmail(autor.getEmail(), autor.getName(),
                    contenido, request.getReason());
        } catch (Exception ignored) {}

        try {
            notificationService.crearComentarioEliminadoSerie(autor, comment.getSeriesId(), seriesTitle, contenido, "comentario");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("message", "Comentario eliminado y usuario notificado"));
    }

    @PostMapping("/series/{commentId}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> restoreSeriesComment(@PathVariable Long commentId) {
        SeriesComment comment = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        User autor = comment.getUser();

        int puntosComentario = comment.getPointsAwarded() != null ? comment.getPointsAwarded() : 0;
        if (puntosComentario > 0) {
            autor.addAccumulatedPoints(puntosComentario);

            String seriesTitle = resolveSeriesTitle(comment.getSeriesId());

            PointTransaction pt = new PointTransaction();
            pt.setUser(autor);
            pt.setType(PointTransactionType.EARNED);
            pt.setAction(PointAction.COMMENT_SERIES);
            pt.setPoints(puntosComentario);
            pt.setReferenceId(comment.getSeriesId());
            pt.setReferenceTitle("Comentario restaurado en serie: " + seriesTitle);
            pointTransactionRepository.save(pt);
        }

        List<SeriesCommentReaction> merecePuntos = seriesCommentReactionRepository
                .findAllByCommentIdAndType(commentId, ReactionType.MERECE_PUNTO);

        for (SeriesCommentReaction mp : merecePuntos) {
            if (mp.isPointsAwarded()) {
                autor.addAccumulatedPoints(MERECE_PUNTO_POINTS);

                PointTransaction ptMp = new PointTransaction();
                ptMp.setUser(autor);
                ptMp.setType(PointTransactionType.EARNED);
                ptMp.setAction(PointAction.RECEIVE_MERECE_PUNTO);
                ptMp.setPoints(MERECE_PUNTO_POINTS);
                ptMp.setReferenceId(commentId);
                ptMp.setReferenceTitle("Merecio un punto restaurado en comentario #" + commentId);
                pointTransactionRepository.save(ptMp);
            }
        }

        userRepository.save(autor);

        comment.setModerationStatus(ModerationStatus.APPROVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        seriesCommentRepository.save(comment);

        return ResponseEntity.ok(Map.of("message", "Comentario restaurado y puntos re-otorgados"));
    }

    @PostMapping("/series/{commentId}/dismiss")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> dismissSeriesComment(@PathVariable Long commentId,
                                                  @RequestParam(defaultValue = "false") boolean isReply) {
        if (isReply) {
            SeriesCommentReply reply = seriesCommentReplyRepository.findById(commentId)
                    .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

            List<SeriesCommentReplyReport> reports = seriesCommentReplyReportRepository.findByReplyIdOrderByCreatedAtDesc(commentId);
            for (SeriesCommentReplyReport report : reports) {
                User reporter = report.getReporter();
                try {
                    SupportTicket ticket = new SupportTicket();
                    ticket.setUser(reporter);
                    ticket.setSubject("Revisamos tu reporte");
                    ticket.setStatus(TicketStatus.OPEN);
                    SupportTicket savedTicket = supportTicketRepository.save(ticket);

                    String seriesTitle = resolveSeriesTitle(reply.getComment().getSeriesId());
                    String contenidoReplyDisplay = displayContent(reply.getContent(), reply.getHasGif());
                    String contenidoCorto = contenidoReplyDisplay.length() > 200
                            ? contenidoReplyDisplay.substring(0, 200) + "..." : contenidoReplyDisplay;

                    String mensaje = "Hemos revisado la respuesta que reportaste en la serie \"" + seriesTitle + "\":\n\n" +
                            "\"" + contenidoCorto + "\"\n\n" +
                            "La misma no viola ninguno de nuestros reglamentos, política de privacidad ni normas de convivencia. " +
                            "Esperamos que sigas ayudándonos a sanear el contenido con cualquier otra respuesta que creas que " +
                            "viola alguna regla de nuestra comunidad. Saludos.";

                    SupportMessage msg = new SupportMessage();
                    msg.setTicket(savedTicket);
                    msg.setSenderType(SenderType.ADMIN);
                    msg.setSenderName("Cinemarketer");
                    msg.setContent(mensaje);
                    msg.setReadByAdmin(true);
                    msg.setReadByUser(false);
                    supportMessageRepository.save(msg);
                } catch (Exception ignored) {}
            }
            seriesCommentReplyReportRepository.deleteByReplyId(commentId);

            reply.setReportCount(0);
            reply.setModerationStatus(ModerationStatus.DISMISSED);
            reply.setAdminReviewed(true);
            reply.setModerationReviewedAt(LocalDateTime.now());
            seriesCommentReplyRepository.save(reply);
        } else {
            SeriesComment comment = seriesCommentRepository.findById(commentId)
                    .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

            List<SeriesCommentReport> reports = seriesCommentReportRepository.findByCommentIdOrderByCreatedAtDesc(commentId);
            for (SeriesCommentReport report : reports) {
                User reporter = report.getReporter();
                try {
                    SupportTicket ticket = new SupportTicket();
                    ticket.setUser(reporter);
                    ticket.setSubject("Revisamos tu reporte");
                    ticket.setStatus(TicketStatus.OPEN);
                    SupportTicket savedTicket = supportTicketRepository.save(ticket);

                    String seriesTitle = resolveSeriesTitle(comment.getSeriesId());
                    String contenidoDisplay = displayContent(comment.getContent(), comment.getHasGif());
                    String contenidoCorto = contenidoDisplay.length() > 200
                            ? contenidoDisplay.substring(0, 200) + "..." : contenidoDisplay;

                    String mensaje = "Hemos revisado el comentario que reportaste en la serie \"" + seriesTitle + "\":\n\n" +
                            "\"" + contenidoCorto + "\"\n\n" +
                            "El mismo no viola ninguno de nuestros reglamentos, política de privacidad ni normas de convivencia. " +
                            "Esperamos que sigas ayudándonos a sanear el contenido con cualquier otro comentario que creas que " +
                            "viola alguna regla de nuestra comunidad. Saludos.";

                    SupportMessage msg = new SupportMessage();
                    msg.setTicket(savedTicket);
                    msg.setSenderType(SenderType.ADMIN);
                    msg.setSenderName("Cinemarketer");
                    msg.setContent(mensaje);
                    msg.setReadByAdmin(true);
                    msg.setReadByUser(false);
                    supportMessageRepository.save(msg);
                } catch (Exception ignored) {}
            }
            seriesCommentReportRepository.deleteByCommentId(commentId);

            comment.setReportCount(0);
            comment.setModerationStatus(ModerationStatus.DISMISSED);
            comment.setAdminReviewed(true);
            comment.setModerationReviewedAt(LocalDateTime.now());
            seriesCommentRepository.save(comment);
        }
        return ResponseEntity.ok(Map.of("message", "Reporte desestimado correctamente"));
    }

    @PostMapping("/series/replies/{replyId}/remove")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> removeSeriesReply(
            @PathVariable Long replyId,
            @RequestBody CommentRemoveRequest request) {

        if (request.getReason() == null || request.getReason().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El motivo de eliminacion es obligatorio"));
        }

        SeriesCommentReply reply = seriesCommentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        User autor = reply.getUser();
        SeriesComment comentarioPadre = reply.getComment();
        String contenidoReply = displayContent(reply.getContent(), reply.getHasGif());
        String contenidoPadre = displayContent(comentarioPadre.getContent(), comentarioPadre.getHasGif());

        String seriesTitle = resolveSeriesTitle(comentarioPadre.getSeriesId());

        reply.setModerationStatus(ModerationStatus.REMOVED);
        reply.setModerationReviewedAt(LocalDateTime.now());
        seriesCommentReplyRepository.save(reply);

        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUser(autor);
            ticket.setSubject("Tu respuesta fue eliminada");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            String extractoReply  = contenidoReply.length()  > 100 ? contenidoReply.substring(0, 100)  + "..." : contenidoReply;
            String extractoPadre  = contenidoPadre.length()  > 100 ? contenidoPadre.substring(0, 100)  + "..." : contenidoPadre;

            String mensajeTicket = String.format(
                    "Tu respuesta \"%s\" sobre el comentario \"%s\" en la serie \"%s\" " +
                            "fue eliminada por no cumplir con nuestras normas de convivencia.\n\n" +
                            "Motivo:\n%s\n\n" +
                            "Si tenes consultas al respecto, podes responder este mensaje.",
                    extractoReply, extractoPadre, seriesTitle, request.getReason());

            SupportMessage mensaje = new SupportMessage();
            mensaje.setTicket(savedTicket);
            mensaje.setSenderType(SenderType.ADMIN);
            mensaje.setSenderName("Cinemarketer");
            mensaje.setContent(mensajeTicket);
            mensaje.setReadByAdmin(true);
            mensaje.setReadByUser(false);
            supportMessageRepository.save(mensaje);
        } catch (Exception ignored) {}

        try {
            notificationService.crearComentarioEliminadoSerie(autor, comentarioPadre.getSeriesId(), seriesTitle, contenidoReply, "respuesta");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("message", "Respuesta eliminada y usuario notificado"));
    }

    @PostMapping("/series/replies/{replyId}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> restoreSeriesReply(@PathVariable Long replyId) {
        SeriesCommentReply reply = seriesCommentReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Respuesta no encontrada"));

        reply.setModerationStatus(ModerationStatus.APPROVED);
        seriesCommentReplyRepository.save(reply);

        return ResponseEntity.ok(Map.of("message", "Respuesta restaurada correctamente"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // COMENTARIOS DE PUBLICACIONES (Comunidad) — independiente de películas
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/publications/{commentId}/mark-reviewed")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> markPublicationCommentReviewed(@PathVariable Long commentId) {
        publicationCommentRepository.findById(commentId).ifPresent(c -> {
            c.setAdminReviewed(true);
            publicationCommentRepository.save(c);
        });
        return ResponseEntity.ok(Map.of("message", "Marcado como revisado"));
    }

    @PostMapping("/publications/{commentId}/remove")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> removePublicationComment(
            @PathVariable Long commentId,
            @RequestBody CommentRemoveRequest request) {

        if (request.getReason() == null || request.getReason().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El motivo de eliminacion es obligatorio"));
        }

        PublicationComment comment = publicationCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        User autor = comment.getUser();
        String contenido = comment.getContent();
        Long publicationId = comment.getPublication().getId();

        comment.setModerationStatus(PublicationCommentModerationStatus.REMOVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        publicationCommentRepository.save(comment);

        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUser(autor);
            ticket.setSubject("Tu comentario fue eliminado");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            String tituloPubDisplay = (comment.getPublication().getTitle() != null && !comment.getPublication().getTitle().isBlank())
                    ? comment.getPublication().getTitle() : "(sin título)";
            String mensajeTicket = String.format(
                    "Tu comentario fue eliminado por no cumplir con nuestras politicas de convivencia.\n\n" +
                            "Publicación: \"%s\"\n\n" +
                            "Comentario eliminado:\n\"%s\"\n\nMotivo:\n%s\n\n" +
                            "Si tenes consultas al respecto, podes responder este mensaje.",
                    tituloPubDisplay,
                    contenido.length() > 200 ? contenido.substring(0, 200) + "..." : contenido,
                    request.getReason());

            SupportMessage mensaje = new SupportMessage();
            mensaje.setTicket(savedTicket);
            mensaje.setSenderType(SenderType.ADMIN);
            mensaje.setSenderName("Cinemarketer");
            mensaje.setContent(mensajeTicket);
            mensaje.setReadByAdmin(true);
            mensaje.setReadByUser(false);
            supportMessageRepository.save(mensaje);
        } catch (Exception ignored) {}

        try {
            emailService.sendCommentRemovedEmail(autor.getEmail(), autor.getName(),
                    contenido, request.getReason());
        } catch (Exception ignored) {}

        try {
            notificationService.crearComentarioPublicacionEliminado(autor, publicationId, comment.getPublication().getTitle(), contenido);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("message", "Comentario eliminado y usuario notificado"));
    }

    @PostMapping("/publications/{commentId}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> restorePublicationComment(@PathVariable Long commentId) {
        PublicationComment comment = publicationCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        comment.setModerationStatus(PublicationCommentModerationStatus.APPROVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        publicationCommentRepository.save(comment);

        return ResponseEntity.ok(Map.of("message", "Comentario restaurado correctamente"));
    }

    @PostMapping("/publications/{commentId}/dismiss")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> dismissPublicationComment(@PathVariable Long commentId) {
        PublicationComment comment = publicationCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        List<PublicationReport> reports = publicationReportRepository
                .findByPublicationCommentIdOrderByCreatedAtDesc(commentId);
        for (PublicationReport report : reports) {
            User reporter = report.getUser();
            try {
                SupportTicket ticket = new SupportTicket();
                ticket.setUser(reporter);
                ticket.setSubject("Revisamos tu reporte");
                ticket.setStatus(TicketStatus.OPEN);
                SupportTicket savedTicket = supportTicketRepository.save(ticket);

                String tituloPub = (comment.getPublication().getTitle() != null && !comment.getPublication().getTitle().isBlank())
                        ? "\"" + comment.getPublication().getTitle() + "\"" : "(sin título)";
                String contenidoCorto = comment.getContent().length() > 200
                        ? comment.getContent().substring(0, 200) + "..." : comment.getContent();

                String mensaje = "Hemos revisado el comentario que reportaste en la publicación " + tituloPub + ":\n\n" +
                        "\"" + contenidoCorto + "\"\n\n" +
                        "El mismo no viola ninguno de nuestros reglamentos, política de privacidad ni normas de convivencia. " +
                        "Esperamos que sigas ayudándonos a sanear el contenido con cualquier otro comentario que creas que viola alguna " +
                        "regla de nuestra comunidad. Saludos.";

                SupportMessage msg = new SupportMessage();
                msg.setTicket(savedTicket);
                msg.setSenderType(SenderType.ADMIN);
                msg.setSenderName("Cinemarketer");
                msg.setContent(mensaje);
                msg.setReadByAdmin(true);
                msg.setReadByUser(false);
                supportMessageRepository.save(msg);
            } catch (Exception ignored) {}
        }
        publicationReportRepository.deleteByPublicationCommentId(commentId);

        comment.setReportCount(0);
        comment.setModerationStatus(PublicationCommentModerationStatus.DISMISSED);
        comment.setAdminReviewed(true);
        comment.setModerationReviewedAt(LocalDateTime.now());
        publicationCommentRepository.save(comment);

        return ResponseEntity.ok(Map.of("message", "Reporte desestimado correctamente"));
    }

    /**
     * Intenta restar puntos en este orden:
     * 1. De availablePoints si tiene saldo suficiente
     * 2. De accumulatedPoints si tiene saldo suficiente
     * 3. No hace nada si ninguno tiene saldo suficiente
     * Retorna true si se restaron, false si no habia saldo.
     */
    private boolean revertirPuntos(User user, int puntos) {
        if (puntos <= 0) return false;
        if (user.getAvailablePoints() >= puntos) {
            user.setAvailablePoints(user.getAvailablePoints() - puntos);
            return true;
        } else if (user.getAccumulatedPoints() >= puntos) {
            user.setAccumulatedPoints(user.getAccumulatedPoints() - puntos);
            return true;
        }
        return false;
    }

    // Busca el título de la película: primero en la BD local, y si no está
    // cacheada, la consulta directo a TMDb como refuerzo.
    private String resolveMovieTitle(Long movieId) {
        return movieRepository.findByTmdbId(movieId)
                .map(Movie::getTitle)
                .orElseGet(() -> {
                    try {
                        var dto = tmdbService.getMovieDetails(movieId);
                        return (dto != null && dto.getTitle() != null && !dto.getTitle().isBlank())
                                ? dto.getTitle() : "Pelicula #" + movieId;
                    } catch (Exception e) {
                        return "Pelicula #" + movieId;
                    }
                });
    }

    // Si el "comentario" es en realidad un GIF, el content queda vacío —
    // mostramos un placeholder legible en vez de una cita vacía.
    private String displayContent(String content, Boolean hasGif) {
        if (content != null && !content.isBlank()) return content;
        if (Boolean.TRUE.equals(hasGif)) return "[GIF]";
        return "(sin contenido de texto)";
    }

    private CommentModerationDto toDto(Comment c) {
        List<CommentReport> reports = commentReportRepository
                .findByCommentIdOrderByCreatedAtDesc(c.getId());

        List<CommentModerationDto.ReportDetail> reportDetails = reports.stream()
                .map(r -> new CommentModerationDto.ReportDetail(
                        r.getId(),
                        r.getReporter().getId(),
                        r.getReporter().getName(),
                        r.getReporter().getEmail(),
                        r.getReason(),
                        r.getDescription(),
                        r.getCreatedAt()))
                .collect(Collectors.toList());

        return new CommentModerationDto(
                c.getId(), c.getContent(), c.getHasGif(), c.getGifUrl(), c.getCreatedAt(),
                c.getModerationStatus(), c.getToxicityScore(), c.getReportCount(),
                c.getModerationReviewedAt(), c.getMovieId(),
                c.getUser().getId(), c.getUser().getName(), c.getUser().getEmail(),
                reportDetails, false, null);
    }

    private CommentModerationDto toDtoFromReply(CommentReply r) {
        List<CommentReplyReport> reports = commentReplyReportRepository.findByReplyIdOrderByCreatedAtDesc(r.getId());
        List<CommentModerationDto.ReportDetail> reportDetails = reports.stream()
                .map(rep -> new CommentModerationDto.ReportDetail(
                        rep.getId(), rep.getReporter().getId(), rep.getReporter().getName(),
                        rep.getReporter().getEmail(), rep.getReason(), rep.getDescription(), rep.getCreatedAt()))
                .collect(Collectors.toList());

        return new CommentModerationDto(
                r.getComment().getId(), r.getContent(), r.getHasGif(), r.getGifUrl(), r.getCreatedAt(),
                r.getModerationStatus(), null, r.getReportCount(), r.getModerationReviewedAt(),
                r.getComment().getMovieId(),
                r.getUser().getId(), r.getUser().getName(), r.getUser().getEmail(),
                reportDetails, true, r.getId());
    }

    private com.example.demo.application.dtos.PublicationCommentModerationDto toDtoFromPublicationComment(PublicationComment c) {
        List<PublicationReport> reports = publicationReportRepository
                .findByPublicationCommentIdOrderByCreatedAtDesc(c.getId());

        List<com.example.demo.application.dtos.PublicationCommentModerationDto.ReportDetail> reportDetails =
                reports.stream()
                        .map(r -> new com.example.demo.application.dtos.PublicationCommentModerationDto.ReportDetail(
                                r.getId(),
                                r.getUser().getId(),
                                r.getUser().getName(),
                                r.getUser().getEmail(),
                                r.getReason(),
                                r.getDescription(),
                                r.getCreatedAt()))
                        .collect(Collectors.toList());

        return new com.example.demo.application.dtos.PublicationCommentModerationDto(
                c.getId(), c.getContent(), c.getCreatedAt(),
                c.getModerationStatus(), c.getReportCount(), c.getModerationReviewedAt(),
                c.getPublication().getId(),
                c.getUser().getId(), c.getUser().getName(), c.getUser().getEmail(),
                reportDetails);
    }

    // Busca el título de la serie: primero en la BD local, y si no está
    // cacheada, la consulta directo a TMDb como refuerzo.
    private String resolveSeriesTitle(Long seriesId) {
        return seriesRepository.findByTmdbId(seriesId)
                .map(Series::getTitle)
                .orElseGet(() -> {
                    try {
                        var dto = seriesService.getSeriesDetails(seriesId);
                        return (dto != null && dto.getName() != null && !dto.getName().isBlank())
                                ? dto.getName() : "Serie #" + seriesId;
                    } catch (Exception e) {
                        return "Serie #" + seriesId;
                    }
                });
    }

    private SeriesCommentModerationDto toDtoFromSeries(SeriesComment c) {
        List<SeriesCommentReport> reports = seriesCommentReportRepository
                .findByCommentIdOrderByCreatedAtDesc(c.getId());

        List<SeriesCommentModerationDto.ReportDetail> reportDetails = reports.stream()
                .map(r -> new SeriesCommentModerationDto.ReportDetail(
                        r.getId(),
                        r.getReporter().getId(),
                        r.getReporter().getName(),
                        r.getReporter().getEmail(),
                        r.getReason(),
                        r.getDescription(),
                        r.getCreatedAt()))
                .collect(Collectors.toList());

        return new SeriesCommentModerationDto(
                c.getId(), c.getContent(), c.getHasGif(), c.getGifUrl(), c.getCreatedAt(),
                c.getModerationStatus(), c.getToxicityScore(), c.getReportCount(),
                c.getModerationReviewedAt(), c.getSeriesId(),
                c.getUser().getId(), c.getUser().getName(), c.getUser().getEmail(),
                reportDetails, false, null);
    }

    private SeriesCommentModerationDto toDtoFromSeriesReply(SeriesCommentReply r) {
        List<SeriesCommentReplyReport> reports = seriesCommentReplyReportRepository.findByReplyIdOrderByCreatedAtDesc(r.getId());
        List<SeriesCommentModerationDto.ReportDetail> reportDetails = reports.stream()
                .map(rep -> new SeriesCommentModerationDto.ReportDetail(
                        rep.getId(), rep.getReporter().getId(), rep.getReporter().getName(),
                        rep.getReporter().getEmail(), rep.getReason(), rep.getDescription(), rep.getCreatedAt()))
                .collect(Collectors.toList());

        return new SeriesCommentModerationDto(
                r.getComment().getId(), r.getContent(), r.getHasGif(), r.getGifUrl(), r.getCreatedAt(),
                r.getModerationStatus(), null, r.getReportCount(), r.getModerationReviewedAt(),
                r.getComment().getSeriesId(),
                r.getUser().getId(), r.getUser().getName(), r.getUser().getEmail(),
                reportDetails, true, r.getId());
    }
}


