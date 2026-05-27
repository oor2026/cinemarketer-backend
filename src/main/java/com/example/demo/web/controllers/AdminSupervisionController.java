package com.example.demo.web.controllers;

import com.example.demo.application.dtos.CommentModerationDto;
import com.example.demo.application.dtos.CommentRemoveRequest;
import com.example.demo.application.services.EmailService;
import com.example.demo.domain.comment.*;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
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
    private final SupportTicketRepository     supportTicketRepository;
    private final SupportMessageRepository    supportMessageRepository;
    private final PointTransactionRepository  pointTransactionRepository;
    private final UserRepository              userRepository;
    private final MovieRepository             movieRepository;
    private final EmailService                emailService;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Long>> getStats() {
        long reportados = commentRepository.findReported().size();
        long pendientes = commentRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.PENDING_REVIEW).size();
        long resueltos  = commentRepository.findResolved().size();
        long repliesRep = commentReplyRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.PENDING_REVIEW).size();

        return ResponseEntity.ok(Map.of(
                "reportados",  reportados,
                "pendientes",  pendientes,
                "resueltos",   resueltos,
                "repliesRep",  repliesRep
        ));
    }

    @GetMapping("/reported")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getReported() {
        return ResponseEntity.ok(commentRepository.findReported().stream()
                .map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getPending() {
        return ResponseEntity.ok(commentRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.PENDING_REVIEW).stream()
                .map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/resolved")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getResolved() {
        return ResponseEntity.ok(commentRepository.findResolved().stream()
                .map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/replies-reported")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getRepliesReported() {
        return ResponseEntity.ok(commentReplyRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.PENDING_REVIEW).stream()
                .map(this::toDtoFromReply).collect(Collectors.toList()));
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
        String contenido = comment.getContent();

        String movieTitle = movieRepository.findByTmdbId(comment.getMovieId())
                .map(Movie::getTitle).orElse("Pelicula #" + comment.getMovieId());

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

            String movieTitle = movieRepository.findByTmdbId(comment.getMovieId())
                    .map(Movie::getTitle).orElse("Pelicula #" + comment.getMovieId());

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
    public ResponseEntity<?> dismissReports(@PathVariable Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        commentReportRepository.deleteByCommentId(commentId);
        comment.setReportCount(0);
        comment.setModerationStatus(ModerationStatus.APPROVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of("message", "Reportes descartados. El comentario sigue visible."));
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
        String contenidoReply = reply.getContent();
        String contenidoPadre = comentarioPadre.getContent();

        String movieTitle = movieRepository.findByTmdbId(comentarioPadre.getMovieId())
                .map(Movie::getTitle).orElse("Pelicula #" + comentarioPadre.getMovieId());

        reply.setModerationStatus(ModerationStatus.REMOVED);
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
                c.getId(), c.getContent(), c.getCreatedAt(),
                c.getModerationStatus(), c.getToxicityScore(), c.getReportCount(),
                c.getModerationReviewedAt(), c.getMovieId(),
                c.getUser().getId(), c.getUser().getName(), c.getUser().getEmail(),
                reportDetails, false, null);
    }

    private CommentModerationDto toDtoFromReply(CommentReply r) {
        return new CommentModerationDto(
                r.getComment().getId(), r.getContent(), r.getCreatedAt(),
                r.getModerationStatus(), null, 0, null,
                r.getComment().getMovieId(),
                r.getUser().getId(), r.getUser().getName(), r.getUser().getEmail(),
                List.of(), true, r.getId());
    }
}