package com.example.demo.web.controllers;

import com.example.demo.application.dtos.AdminUserDto;
import com.example.demo.application.dtos.AdminUserUpdateRequest;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.pointtransaction.PointTransactionRepository;
import com.example.demo.domain.support.SupportMessageRepository;
import com.example.demo.domain.support.SupportTicket;
import com.example.demo.domain.support.SupportTicketRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.application.services.EmailService;
import com.example.demo.domain.user.UserRole;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedemptionRepository redemptionRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final EmailService emailService;
    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;

    public AdminUserController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RedemptionRepository redemptionRepository,
            ReviewRepository reviewRepository,
            CommentRepository commentRepository,
            PointTransactionRepository pointTransactionRepository,
            SupportTicketRepository ticketRepository,
            SupportMessageRepository messageRepository,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redemptionRepository = redemptionRepository;
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.emailService = emailService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "todos") String filter) {

        Page<User> pageResult;

        if (search != null && !search.trim().isEmpty()) {
            pageResult = userRepository.findByNameContainingOrEmailContaining(
                    search, search, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        } else {
            pageResult = switch (filter) {
                case "activos"     -> userRepository.findBySuspendedFalseAndActiveTrue(
                        PageRequest.of(page, size, Sort.by("createdAt").descending()));
                case "suspendidos" -> userRepository.findBySuspendedTrue(
                        PageRequest.of(page, size, Sort.by("createdAt").descending()));
                default            -> userRepository.findAll(
                        PageRequest.of(page, size, Sort.by("createdAt").descending()));
            };
        }

        Map<String, Object> response = new HashMap<>();
        response.put("users", pageResult.getContent().stream().map(this::toDto).toList());
        response.put("currentPage", pageResult.getNumber());
        response.put("totalItems", pageResult.getTotalElements());
        response.put("totalPages", pageResult.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDto> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody AdminUserUpdateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El email " + request.getEmail() + " ya está registrado por otro usuario."));
        }

        if (request.getDni() != null && !request.getDni().isBlank() && userRepository.existsByDni(request.getDni())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El DNI " + request.getDni() + " ya está registrado por otro usuario."));
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setDni(request.getDni());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode("temporal123"));
        user.setRole(request.getRole() != null ? request.getRole() : UserRole.USER);
        user.setActive(request.getActive() != null ? request.getActive() : true);

        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        user.setEmailVerified(false);

        userRepository.save(user);

        try {
            emailService.sendVerificationEmail(user.getEmail(), verificationToken);
        } catch (Exception e) {
            // Silencio - no interrumpir el flujo
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(user));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestBody AdminUserUpdateRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String nuevoEmail = request.getEmail();
        boolean emailCambio = nuevoEmail != null && !nuevoEmail.equalsIgnoreCase(user.getEmail());

        if (emailCambio) {
            if (userRepository.existsByEmail(nuevoEmail)) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "El email " + nuevoEmail + " ya está registrado por otro usuario."));
            }
        }

        String nuevoDni = request.getDni();
        boolean dniCambio = nuevoDni != null && !nuevoDni.equalsIgnoreCase(user.getDni());

        if (dniCambio && userRepository.existsByDni(nuevoDni)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El DNI " + nuevoDni + " ya está registrado por otro usuario."));
        }

        user.setName(request.getName());
        user.setEmail(nuevoEmail);
        user.setDni(request.getDni());
        user.setPhone(request.getPhone());
        if (request.getRole() != null) user.setRole(request.getRole());
        if (request.getTotalPoints() != null) user.setTotalPoints(request.getTotalPoints());
        if (request.getActive() != null) user.setActive(request.getActive());

        if (emailCambio) {
            String verificationToken = UUID.randomUUID().toString();
            user.setVerificationToken(verificationToken);
            user.setEmailVerified(false);
            userRepository.save(user);

            try {
                emailService.sendEmailChangeVerification(nuevoEmail, verificationToken);
            } catch (Exception e) {
                // Silencio
            }
        } else {
            userRepository.save(user);
        }

        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/{id}/suspend")
    @Transactional
    public ResponseEntity<AdminUserDto> suspendUser(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String reason = body.get("reason");
        user.suspend(reason);
        userRepository.save(user);

        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/{id}/unsuspend")
    @Transactional
    public ResponseEntity<AdminUserDto> unsuspendUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.unsuspend();
        userRepository.save(user);

        return ResponseEntity.ok(toDto(user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

            User currentUser = getCurrentUser();
            if (currentUser != null && currentUser.getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No puedes eliminar tu propia cuenta"));
            }

            commentRepository.deleteByUser(user);

            if (user.getReviews() != null && !user.getReviews().isEmpty()) {
                reviewRepository.deleteAll(user.getReviews());
            }

            if (user.getRedemptions() != null && !user.getRedemptions().isEmpty()) {
                redemptionRepository.deleteAll(user.getRedemptions());
            }

            pointTransactionRepository.deleteAll(pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, Integer.MAX_VALUE)).getContent());

            List<SupportTicket> tickets = ticketRepository.findByUserId(user.getId());
            for (SupportTicket ticket : tickets) {
                messageRepository.deleteByTicketId(ticket.getId());
            }
            ticketRepository.deleteAll(tickets);

            userRepository.delete(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Usuario eliminado correctamente",
                    "id", id
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno al eliminar usuario: " + e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long total = userRepository.count();
        long suspended = userRepository.countBySuspended(true);
        long active = total - suspended;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("active", active);
        stats.put("suspended", suspended);
        stats.put("verified", userRepository.countByEmailVerified(true));

        return ResponseEntity.ok(stats);
    }

    // =============================================
    // HELPERS
    // =============================================
    private AdminUserDto toDto(User user) {
        AdminUserDto dto = new AdminUserDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setDni(user.getDni());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setTotalPoints(user.getTotalPoints());
        // 🔧 CORREGIDO: usar isActive() en lugar de getActive()
        dto.setActive(user.isActive());
        dto.setSuspended(user.isSuspended());
        dto.setSuspensionReason(user.getSuspensionReason());
        dto.setSuspendedAt(user.getSuspendedAt());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        // 🔧 CORREGIDO: usar isEmailVerified() en lugar de getEmailVerified()
        dto.setEmailVerified(user.isEmailVerified());
        return dto;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            String email = ((UserDetails) principal).getUsername();
            return userRepository.findByEmail(email).orElse(null);
        }
        return null;
    }
}