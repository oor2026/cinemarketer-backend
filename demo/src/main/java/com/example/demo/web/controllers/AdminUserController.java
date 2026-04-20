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

    // Constructor actualizado con PointTransactionRepository
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

    /**
     * Obtener todos los usuarios (con paginación)blo
     * GET /api/admin/users?page=0&size=20&search=
     */
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

    /**
     * Obtener usuario por ID
     * GET /api/admin/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDto> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(toDto(user));
    }

    /**
     * Crear nuevo usuario (equivalente a registro)
     * POST /api/admin/users
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody AdminUserUpdateRequest request) {
        // Verificar si el email ya existe
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El email " + request.getEmail() + " ya está registrado por otro usuario."));
        }

        // Verificar si el DNI ya existe
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
        user.setPassword(passwordEncoder.encode("temporal123")); // Contraseña temporal
        user.setRole(request.getRole() != null ? request.getRole() : UserRole.USER);
        user.setActive(request.getActive() != null ? request.getActive() : true);

        // Generar token de verificación — el usuario debe confirmar su email
        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        user.setEmailVerified(false);

        userRepository.save(user);

        // Enviar mail de verificación con el mismo template del registro
        try {
            emailService.sendVerificationEmail(user.getEmail(), verificationToken);
            System.out.println("✉️ Mail de verificación enviado al nuevo usuario: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("⚠️ Error enviando mail de verificación: " + e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(user));
    }

    /**
     * Actualizar usuario
     * PUT /api/admin/users/{id}
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestBody AdminUserUpdateRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // ── Control de email duplicado ────────────────────────────────────────
        String nuevoEmail = request.getEmail();
        boolean emailCambio = nuevoEmail != null && !nuevoEmail.equalsIgnoreCase(user.getEmail());

        if (emailCambio) {
            if (userRepository.existsByEmail(nuevoEmail)) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "El email " + nuevoEmail + " ya está registrado por otro usuario."));
            }
        }

        // ── Control de DNI duplicado ──────────────────────────────────────────
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

        // ── Si el email cambió: marcar no verificado y enviar mail ────────────
        if (emailCambio) {
            String verificationToken = UUID.randomUUID().toString();
            user.setVerificationToken(verificationToken);
            user.setEmailVerified(false);
            userRepository.save(user);

            try {
                emailService.sendEmailChangeVerification(nuevoEmail, verificationToken);
                System.out.println("✉️ Mail de verificación enviado al nuevo email: " + nuevoEmail);
            } catch (Exception e) {
                System.err.println("⚠️ Error enviando mail de verificación: " + e.getMessage());
                // No frenamos la operación, el usuario fue guardado igual
            }
        } else {
            userRepository.save(user);
        }

        return ResponseEntity.ok(toDto(user));
    }

    /**
     * Suspender usuario (inhabilitar)
     * POST /api/admin/users/{id}/suspend
     */
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

    /**
     * Reactivar usuario (quitar suspensión)
     * POST /api/admin/users/{id}/unsuspend
     */
    @PostMapping("/{id}/unsuspend")
    @Transactional
    public ResponseEntity<AdminUserDto> unsuspendUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.unsuspend();
        userRepository.save(user);

        return ResponseEntity.ok(toDto(user));
    }

    /**
     * Eliminar usuario (permanente) - CON ELIMINACIÓN EN CASCADA COMPLETA
     * DELETE /api/admin/users/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        System.out.println("🔍 Admin intenta eliminar usuario ID: " + id);

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

            System.out.println("👤 Usuario encontrado: " + user.getEmail() + " | Rol: " + user.getRole());

            // Solo evitar que un admin se elimine a sí mismo
            User currentUser = getCurrentUser();
            if (currentUser != null && currentUser.getId().equals(id)) {
                System.out.println("⛔ Admin intenta eliminarse a sí mismo - BLOQUEADO");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No puedes eliminar tu propia cuenta"));
            }

            // 🔥 ELIMINACIÓN EN CASCADA COMPLETA
            System.out.println("🗑️ Iniciando eliminación en cascada para usuario ID: " + id);

            // 1. Primero comments (dependen directamente del user)
            commentRepository.deleteByUser(user);
            System.out.println("✅ Comments del usuario eliminados");

            // 2. Luego reviews (dependen del user)
            if (user.getReviews() != null && !user.getReviews().isEmpty()) {
                System.out.println("🗑️ Eliminando " + user.getReviews().size() + " reviews del usuario");
                reviewRepository.deleteAll(user.getReviews());
            } else {
                System.out.println("ℹ️ No hay reviews para eliminar");
            }

            // 3. Luego redemptions (dependen del user)
            if (user.getRedemptions() != null && !user.getRedemptions().isEmpty()) {
                System.out.println("🗑️ Eliminando " + user.getRedemptions().size() + " canjes del usuario");
                redemptionRepository.deleteAll(user.getRedemptions());
            } else {
                System.out.println("ℹ️ No hay canjes para eliminar");
            }

            // 4. Eliminar point_transactions
            // Como el repositorio no tiene deleteByUser, obtenemos las transacciones y las eliminamos
            System.out.println("🗑️ Eliminando transacciones de puntos del usuario");
            pointTransactionRepository.deleteAll(pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, Integer.MAX_VALUE)).getContent());
            System.out.println("✅ Transacciones de puntos eliminadas");

            // 5. Eliminar support tickets y sus mensajes
            List<SupportTicket> tickets = ticketRepository.findByUserId(user.getId());
            for (SupportTicket ticket : tickets) {
                messageRepository.deleteByTicketId(ticket.getId());
            }
            ticketRepository.deleteAll(tickets);
            System.out.println("✅ Tickets de soporte y mensajes eliminados");

            // 6. Finalmente el usuario
            userRepository.delete(user);
            System.out.println("✅ Usuario eliminado correctamente por admin: " + id);

            return ResponseEntity.ok(Map.of(
                    "message", "Usuario eliminado correctamente",
                    "id", id
            ));

        } catch (Exception e) {
            System.err.println("❌ Error eliminando usuario: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno al eliminar usuario: " + e.getMessage()));
        }
    }

    /**
     * Estadísticas
     * GET /api/admin/users/stats
     */
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
        dto.setActive(user.getActive());
        dto.setSuspended(user.isSuspended());
        dto.setSuspensionReason(user.getSuspensionReason());
        dto.setSuspendedAt(user.getSuspendedAt());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setEmailVerified(user.isEmailVerified());
        return dto;
    }

    /**
     * Obtener el usuario actualmente autenticado
     */
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