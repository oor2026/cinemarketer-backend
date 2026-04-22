package com.example.demo.web.controllers;

import com.example.demo.application.dtos.*;
import com.example.demo.domain.support.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/support")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
public class AdminSupportController {

    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;

    // ── Listar todos los tickets ──────────────────────────────────────────────
    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> getAllTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "todos") String filter) {

        PageRequest pageable = PageRequest.of(page, size);

        org.springframework.data.domain.Page<SupportTicket> pageResult = switch (filter) {
            case "abiertos" -> ticketRepository.findByDeletedByAdminFalseAndOpenAndRead(pageable);
            case "cerrados"  -> ticketRepository.findByDeletedByAdminFalseAndStatusOrderByCreatedAtDesc(
                    TicketStatus.CLOSED, pageable);
            case "sinleer"   -> ticketRepository.findByDeletedByAdminFalseAndUnread(pageable);
            default          -> ticketRepository.findByDeletedByAdminFalseOrderByCreatedAtDesc(pageable);
        };

        List<SupportTicketSummaryDto> result = pageResult.getContent()
                .stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("tickets", result);
        response.put("currentPage", pageResult.getNumber());
        response.put("totalPages", pageResult.getTotalPages());
        response.put("totalItems", pageResult.getTotalElements());

        return ResponseEntity.ok(response);
    }

    // ── Ver hilo completo de un ticket ────────────────────────────────────────
    @GetMapping("/tickets/{id}/messages")
    @Transactional
    public ResponseEntity<?> getMessages(@PathVariable Long id) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));

        // Marcar mensajes del usuario como leídos por el admin
        messageRepository.markAsReadByAdmin(id);

        return ResponseEntity.ok(toTicketDto(ticket));
    }

    // ── Responder en un ticket ────────────────────────────────────────────────
    @PostMapping("/tickets/{id}/messages")
    @Transactional
    public ResponseEntity<?> replyMessage(@PathVariable Long id,
                                          @RequestBody SendMessageRequest request,
                                          Authentication auth) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));

        if (!ticket.isOpen()) {
            return ResponseEntity.badRequest().body("Este ticket está cerrado.");
        }

        if (request.getContent() == null ||
            request.getContent().replaceAll("\\s", "").length() < 1) {
            return ResponseEntity.badRequest().body("El mensaje no puede estar vacío.");
        }

        // Si el usuario había "eliminado" este ticket de su bandeja, lo restauramos
        // para que vea la nueva respuesta del admin
        if (Boolean.TRUE.equals(ticket.getDeletedByUser())) {
            ticket.setDeletedByUser(false);
            ticketRepository.save(ticket);
        }

        SupportMessage message = new SupportMessage();
        message.setTicket(ticket);
        message.setSenderType(SenderType.ADMIN);
        message.setSenderName("Soporte Cinemarketer");
        message.setContent(request.getContent());
        message.setReadByAdmin(true);
        message.setReadByUser(false);
        messageRepository.save(message);

        return ResponseEntity.ok(toMessageDto(message));
    }

    // ── Cerrar ticket ─────────────────────────────────────────────────────────
    @PatchMapping("/tickets/{id}/close")
    @Transactional
    public ResponseEntity<?> closeTicket(@PathVariable Long id, Authentication auth) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));

        if (!ticket.isOpen()) {
            return ResponseEntity.badRequest().body("El ticket ya está cerrado.");
        }

        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(LocalDateTime.now());
        ticket.setClosedBy(auth.getName());
        ticketRepository.save(ticket);

        return ResponseEntity.ok("Ticket cerrado correctamente.");
    }

    // ── Eliminar ticket ───────────────────────────────────────────────────────
    @DeleteMapping("/tickets/{id}")
    @Transactional
    public ResponseEntity<?> deleteTicket(@PathVariable Long id) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));
        ticket.setDeletedByAdmin(true);
        ticketRepository.save(ticket);
        return ResponseEntity.ok("Ticket eliminado.");
    }

    // ── Badge: mensajes no leídos por el admin ────────────────────────────────
    @GetMapping("/unread")
    public ResponseEntity<UnreadCountDto> getUnreadCount() {
        long count = ticketRepository.countUnreadByAdmin();
        return ResponseEntity.ok(new UnreadCountDto(count));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────
    private SupportTicketDto toTicketDto(SupportTicket ticket) {
        List<SupportMessageDto> messages = messageRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());

        long unread = messages.stream()
                .filter(m -> m.getSenderType() == SenderType.USER && !m.getReadByAdmin())
                .count();

        return new SupportTicketDto(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getClosedAt(),
                ticket.getClosedBy(),
                ticket.getUser().getId(),
                ticket.getUser().getName(),
                messages,
                unread
        );
    }

    private SupportTicketSummaryDto toSummaryDto(SupportTicket ticket) {
        List<SupportMessage> msgs = messageRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        SupportMessage last = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);

        long unread = msgs.stream()
                .filter(m -> m.getSenderType() == SenderType.USER && !m.getReadByAdmin())
                .count();

        return new SupportTicketSummaryDto(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                last != null ? last.getContent() : "",
                last != null ? last.getCreatedAt() : ticket.getCreatedAt(),
                unread,
                ticket.getUser().getName()
        );
    }

    private SupportMessageDto toMessageDto(SupportMessage m) {
        return new SupportMessageDto(
                m.getId(),
                m.getSenderType(),
                m.getSenderName(),
                m.getContent(),
                m.getCreatedAt(),
                m.getReadByUser(),
                m.getReadByAdmin()
        );
    }
}
