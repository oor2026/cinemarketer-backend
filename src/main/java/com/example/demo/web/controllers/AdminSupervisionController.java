package com.example.demo.web.controllers;

import com.example.demo.application.dtos.CommentModerationDto;
import com.example.demo.application.dtos.CommentRemoveRequest;
import com.example.demo.application.services.EmailService;
import com.example.demo.domain.comment.*;
import com.example.demo.domain.support.*;
import com.example.demo.domain.user.User;
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

    private final CommentRepository        commentRepository;
    private final CommentReportRepository  commentReportRepository;
    private final CommentReplyRepository   commentReplyRepository;
    private final SupportTicketRepository  supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final EmailService             emailService;

    // ── STATS ─────────────────────────────────────────────────────────────────

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

    // ── PESTAÑA 1: REPORTADOS ─────────────────────────────────────────────────

    @GetMapping("/reported")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getReported() {
        return ResponseEntity.ok(commentRepository.findReported().stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ── PESTAÑA 2: PENDIENTES ─────────────────────────────────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getPending() {
        return ResponseEntity.ok(commentRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.PENDING_REVIEW).stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ── PESTAÑA 3: RESUELTOS ──────────────────────────────────────────────────

    @GetMapping("/resolved")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getResolved() {
        return ResponseEntity.ok(commentRepository.findResolved().stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ── PESTAÑA 4: RESPUESTAS REPORTADAS ─────────────────────────────────────

    @GetMapping("/replies-reported")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getRepliesReported() {
        return ResponseEntity.ok(commentReplyRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.PENDING_REVIEW).stream()
                .map(this::toDtoFromReply)
                .collect(Collectors.toList()));
    }

    // ── ACCIONES COMENTARIOS ──────────────────────────────────────────────────

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

        comment.setModerationStatus(ModerationStatus.REMOVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        commentRepository.save(comment);

        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUser(autor);
            ticket.setSubject("Tu comentario fue eliminado");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            String mensajeTicket = String.format(
                    "Tu comentario fue eliminado por no cumplir con nuestras politicas de convivencia.\n\n" +
                            "Comentario eliminado:\n\"%s\"\n\nMotivo:\n%s\n\n" +
                            "Si tenes consultas al respecto, podes responder este mensaje.",
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

        comment.setModerationStatus(ModerationStatus.APPROVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of("message", "Comentario restaurado correctamente"));
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

    // ── ACCIONES RESPUESTAS ───────────────────────────────────────────────────

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

        reply.setModerationStatus(ModerationStatus.REMOVED);
        commentReplyRepository.save(reply);

        return ResponseEntity.ok(Map.of("message", "Respuesta eliminada correctamente"));
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

    // ── MAPPERS ───────────────────────────────────────────────────────────────

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

        CommentModerationDto dto = new CommentModerationDto(
                c.getId(),
                c.getContent(),
                c.getCreatedAt(),
                c.getModerationStatus(),
                c.getToxicityScore(),
                c.getReportCount(),
                c.getModerationReviewedAt(),
                c.getMovieId(),
                c.getUser().getId(),
                c.getUser().getName(),
                c.getUser().getEmail(),
                reportDetails,
                false,
                null);
        return dto;
    }

    private CommentModerationDto toDtoFromReply(CommentReply r) {
        CommentModerationDto dto = new CommentModerationDto(
                r.getComment().getId(),
                r.getContent(),
                r.getCreatedAt(),
                r.getModerationStatus(),
                null,
                0,
                null,
                r.getComment().getMovieId(),
                r.getUser().getId(),
                r.getUser().getName(),
                r.getUser().getEmail(),
                List.of(),
                true,
                r.getId());
        return dto;
    }
}