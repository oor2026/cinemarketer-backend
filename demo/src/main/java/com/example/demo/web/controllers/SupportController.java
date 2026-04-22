package com.example.demo.web.controllers;

import com.example.demo.application.dtos.*;
import com.example.demo.domain.support.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;

    // ── Abrir ticket con primer mensaje ──────────────────────────────────────
    @PostMapping("/tickets")
    @Transactional
    public ResponseEntity<?> openTicket(@RequestBody OpenTicketRequest request,
                                        Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getMessage() == null ||
                request.getMessage().trim().length() < 10) {
            return ResponseEntity.badRequest()
                    .body("El mensaje debe tener al menos 10 caracteres.");
        }

        SupportTicket ticket = new SupportTicket();
        ticket.setUser(user);
        ticket.setSubject(request.getSubject() != null && !request.getSubject().isBlank()
                ? request.getSubject()
                : "Consulta de " + user.getName());
        ticket.setStatus(TicketStatus.OPEN);
        ticket = ticketRepository.save(ticket);

        SupportMessage message = new SupportMessage();
        message.setTicket(ticket);
        message.setSenderType(SenderType.USER);
        message.setSenderName(user.getName());
        message.setContent(request.getMessage());
        message.setReadByAdmin(false);
        message.setReadByUser(true);
        messageRepository.save(message);

        return ResponseEntity.ok(toTicketDto(ticket, user.getId()));
    }

    // ── Listar tickets del usuario ────────────────────────────────────────────
    @GetMapping("/tickets/me")
    public ResponseEntity<Map<String, Object>> getMyTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        org.springframework.data.domain.Page<SupportTicket> pageResult =
                ticketRepository.findByUserPrioritizingUnread(
                        user.getId(), PageRequest.of(page, size));

        List<SupportTicketSummaryDto> result = pageResult.getContent()
                .stream()
                .map(t -> toSummaryDto(t, user.getId()))
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
    public ResponseEntity<?> getMessages(@PathVariable Long id, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));

        if (!ticket.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Acceso denegado");
        }

        // Marcar mensajes del admin como leídos
        messageRepository.markAsReadByUser(id);

        return ResponseEntity.ok(toTicketDto(ticket, user.getId()));
    }

    // ── Enviar mensaje en ticket existente ────────────────────────────────────
    @PostMapping("/tickets/{id}/messages")
    @Transactional
    public ResponseEntity<?> sendMessage(@PathVariable Long id,
                                         @RequestBody SendMessageRequest request,
                                         Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));

        if (!ticket.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Acceso denegado");
        }

        if (!ticket.isOpen()) {
            return ResponseEntity.badRequest().body("Este ticket está cerrado.");
        }

        if (request.getContent() == null ||
                request.getContent().trim().length() < 10) {
            return ResponseEntity.badRequest()
                    .body("El mensaje debe tener al menos 10 caracteres.");
        }

        // Si el admin había "eliminado" este ticket de su bandeja, lo restauramos
        // para que vea la nueva respuesta del usuario
        if (Boolean.TRUE.equals(ticket.getDeletedByAdmin())) {
            ticket.setDeletedByAdmin(false);
            ticketRepository.save(ticket);
        }

        SupportMessage message = new SupportMessage();
        message.setTicket(ticket);
        message.setSenderType(SenderType.USER);
        message.setSenderName(user.getName());
        message.setContent(request.getContent());
        message.setReadByAdmin(false);
        message.setReadByUser(true);
        messageRepository.save(message);

        return ResponseEntity.ok(toMessageDto(message));
    }

    // ── Eliminar ticket (solo el propio usuario) ──────────────────────────────
    @DeleteMapping("/tickets/{id}")
    @Transactional
    public ResponseEntity<?> deleteTicket(@PathVariable Long id, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));

        if (!ticket.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Acceso denegado");
        }

        ticket.setDeletedByUser(true);
        ticketRepository.save(ticket);
        return ResponseEntity.ok("Consulta eliminada.");
    }

    // ── Badge: cantidad de mensajes no leídos ─────────────────────────────────
    @GetMapping("/unread")
    public ResponseEntity<UnreadCountDto> getUnreadCount(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        long count = messageRepository.countUnreadForUser(user.getId());
        return ResponseEntity.ok(new UnreadCountDto(count));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────
    private SupportTicketDto toTicketDto(SupportTicket ticket, Long userId) {
        List<SupportMessageDto> messages = messageRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());

        long unread = messages.stream()
                .filter(m -> m.getSenderType() == SenderType.ADMIN && !m.getReadByUser())
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

    private SupportTicketSummaryDto toSummaryDto(SupportTicket ticket, Long userId) {
        List<SupportMessage> msgs = messageRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        SupportMessage last = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);

        long unread = msgs.stream()
                .filter(m -> m.getSenderType() == SenderType.ADMIN && !m.getReadByUser())
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
