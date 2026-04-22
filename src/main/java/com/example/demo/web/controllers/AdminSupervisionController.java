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

    private final CommentRepository          commentRepository;
    private final CommentReportRepository    commentReportRepository;
    private final SupportTicketRepository    supportTicketRepository;
    private final SupportMessageRepository   supportMessageRepository;
    private final EmailService               emailService;

    // ── STATS ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/supervision/stats
     * Contadores para el badge del sidebar y el header del módulo
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Long>> getStats() {
        long reportados     = commentRepository.findReported().size();
        long pendientes     = commentRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.PENDING_REVIEW).size();
        long resueltos      = commentRepository.findResolved().size();

        return ResponseEntity.ok(Map.of(
            "reportados",  reportados,
            "pendientes",  pendientes,
            "resueltos",   resueltos
        ));
    }

    // ── PESTAÑA 1: REPORTADOS ─────────────────────────────────────────────────

    /**
     * GET /api/admin/supervision/reported
     * Comentarios con al menos 1 reporte, ordenados por cantidad de reportes
     */
    @GetMapping("/reported")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getReported() {
        List<Comment> comments = commentRepository.findReported();
        return ResponseEntity.ok(comments.stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ── PESTAÑA 2: PENDIENTES DE REVISIÓN (Perspective/OpenAI) ───────────────

    /**
     * GET /api/admin/supervision/pending
     * Comentarios con score entre 0.6 y 0.8 sin revisar
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getPending() {
        List<Comment> comments = commentRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.PENDING_REVIEW);
        return ResponseEntity.ok(comments.stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ── PESTAÑA 3: RESUELTOS ──────────────────────────────────────────────────

    /**
     * GET /api/admin/supervision/resolved
     * Historial de comentarios eliminados
     */
    @GetMapping("/resolved")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CommentModerationDto>> getResolved() {
        List<Comment> comments = commentRepository.findResolved();
        return ResponseEntity.ok(comments.stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ── ACCIONES ──────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/supervision/{commentId}/remove
     * Elimina el comentario, notifica al usuario por ticket + email
     */
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

        // Marcar como eliminado
        comment.setModerationStatus(ModerationStatus.REMOVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        commentRepository.save(comment);

        // Notificar por ticket de soporte
        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUser(autor);
            ticket.setSubject("Tu comentario fue eliminado");
            ticket.setStatus(TicketStatus.OPEN);
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            String mensajeTicket = String.format(
                "Tu comentario fue eliminado por no cumplir con nuestras politicas de convivencia.\n\n" +
                "Comentario eliminado:\n\"%s\"\n\n" +
                "Motivo:\n%s\n\n" +
                "Si tenes consultas al respecto, podes responder este mensaje.",
                contenido.length() > 200 ? contenido.substring(0, 200) + "..." : contenido,
                request.getReason()
            );

            SupportMessage mensaje = new SupportMessage();
            mensaje.setTicket(savedTicket);
            mensaje.setSenderType(SenderType.ADMIN);
            mensaje.setSenderName("Cinemarketer");
            mensaje.setContent(mensajeTicket);
            mensaje.setReadByAdmin(true);
            mensaje.setReadByUser(false);
            supportMessageRepository.save(mensaje);
        } catch (Exception e) {

        }

        // Notificar por email
        try {
            emailService.sendCommentRemovedEmail(
                autor.getEmail(),
                autor.getName(),
                contenido,
                request.getReason()
            );
        } catch (Exception e) {

        }

        return ResponseEntity.ok(Map.of("message", "Comentario eliminado y usuario notificado"));
    }

    /**
     * POST /api/admin/supervision/{commentId}/restore
     * Restaura un comentario auto-ocultado o en revisión
     */
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

    /**
     * POST /api/admin/supervision/{commentId}/dismiss
     * Descarta los reportes — el comentario no viola políticas
     */
    @PostMapping("/{commentId}/dismiss")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> dismissReports(@PathVariable Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        // Limpiar reportes y restaurar estado
        commentReportRepository.deleteByCommentId(commentId);
        comment.setReportCount(0);
        comment.setModerationStatus(ModerationStatus.APPROVED);
        comment.setModerationReviewedAt(LocalDateTime.now());
        commentRepository.save(comment);

        return ResponseEntity.ok(Map.of("message", "Reportes descartados. El comentario sigue visible."));
    }

    // ── MAPPER ────────────────────────────────────────────────────────────────

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
                        r.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return new CommentModerationDto(
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
                reportDetails
        );
    }
}
