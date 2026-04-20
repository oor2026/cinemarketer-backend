package com.example.demo.web.controllers;

import com.example.demo.application.dtos.LoginRequest;
import com.example.demo.application.dtos.LoginResponse;
import com.example.demo.application.dtos.RegisterRequest;
import com.example.demo.application.dtos.RegisterResponse;
import com.example.demo.application.services.EmailService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.user.UserRole;
import com.example.demo.application.services.SubscriptionService;
import com.example.demo.infrastructure.security.JwtService;
import com.example.demo.infrastructure.security.TokenBlacklistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SubscriptionService subscriptionService;

    // Constructor actualizado
    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService,
            SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      BindingResult bindingResult) {

        // ==============================================
        // 1. MANEJAR ERRORES DE VALIDACIÓN
        // ==============================================
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error ->
                    errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errors);
        }

        // 2. Verificar si el email ya está registrado
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new RegisterResponse(
                            "El email ya está registrado. Por favor, inicia tu sesión con tus credenciales o si la olvidaste, ingresá a la opción ¿Olvidaste tu contraseña? para recuperarla.",
                            request.getEmail(),
                            false
                    ));
        }

        // 3. Verificar si el DNI ya está registrado
        if (userRepository.existsByDni(request.getDni())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)  // 409 Conflict es más apropiado
                    .body(new RegisterResponse(
                            "Este DNI ya se encuentra registrado en nuestra base de datos. Si cree que es una confusión o alguien está haciendo un uso indebido del mismo, por favor, comuníquese con nosotros para brindarle nuestra ayuda.",
                            request.getEmail(),
                            false
                    ));
        }

        // 4. Crear nuevo usuario
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setDni(request.getDni());
        user.setPhone(request.getPhone());

        // Hashear la contraseña con BCrypt
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(hashedPassword);

        // Generar token de verificación único
        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);

        // Por defecto: rol USER, email no verificado
        user.setRole(UserRole.USER);
        user.setEmailVerified(false);
        user.setActive(true);

        // 5. Guardar en base de datos
        userRepository.save(user);

        // 6. Enviar email de verificación
        try {
            emailService.sendVerificationEmail(user.getEmail(), verificationToken);
        } catch (Exception e) {
            // Log del error pero no fallamos el registro
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new RegisterResponse(
                            "Usuario creado pero falló el envío del email de verificación",
                            user.getEmail(),
                            false
                    ));
        }

        // 7. Respuesta exitosa
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponse(
                        "Usuario registrado exitosamente. Revisa tu email para verificar la cuenta.",
                        user.getEmail(),
                        true
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

        // 1. Buscar usuario por email
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        // 2. Verificar si el usuario existe
        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(
                            null,
                            "Bearer",
                            request.getEmail(),
                            null,
                            0,
                            false
                    ));
        }

        // Verificar si el usuario está suspendido
        if (user.isSuspended()) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new LoginResponse(
                            null,
                            "Bearer",
                            user.getEmail(),
                            user.getRole().name(),
                            user.getTotalPoints(),
                            false,
                            "Hemos determinado que la actividad de tu cuenta ha violado nuestros términos y condiciones. Por este motivo, hemos decidido inhabilitar tu cuenta temporalmente. Esperamos que pronto puedas retomar tu actividad dentro de las condiciones aceptadas al crear tu cuenta."
                    ));
        }

        // 3. Autenticar usuario con Spring Security
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // 4. Verificar si el email está verificado
            if (!user.isEmailVerified()) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new LoginResponse(
                                null,
                                "Bearer",
                                user.getEmail(),
                                user.getRole().name(),
                                user.getTotalPoints(),
                                false
                        ));
            }

            // 5. Actualizar último acceso
            user.setLastLoginAt(java.time.LocalDateTime.now());
            userRepository.save(user);

            // 6. Generar token JWT
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String jwtToken = jwtService.generateToken(userDetails);

            // 7. Devolver respuesta exitosa
            boolean isPremium = subscriptionService.isActivePremium(user.getId());

            LoginResponse response = new LoginResponse();
            response.setToken(jwtToken);
            response.setType("Bearer");
            response.setEmail(user.getEmail());
            response.setRole(user.getRole().name());
            response.setTotalPoints(user.getTotalPoints());
            response.setSuccess(true);
            response.setLevel(user.getLevel());
            response.setPremium(isPremium);

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(
                            null,
                            "Bearer",
                            request.getEmail(),
                            null,
                            0,
                            false
                    ));
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {

        // Buscar usuario por token
        User user = userRepository.findByVerificationToken(token)
                .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Token de verificación inválido");
        }

        if (user.isEmailVerified()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("La cuenta ya estaba verificada");
        }

        // Marcar como verificado y limpiar token
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);

        // Redirigir al frontend (login)
        String redirectUrl = "http://localhost:63342/src/login.html?verified=true";
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header("Location", redirectUrl)
                .build();
    }

    /**
     * Endpoint de logout con blacklist en memoria
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {

        // Verificar que el token existe y tiene el formato correcto
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)  // 401 en lugar de 400
                    .body(Map.of(
                            "error", "Se requiere token de autenticación",
                            "status", "error",
                            "code", "UNAUTHORIZED"
                    ));
        }

        try {
            // Extraer token
            String token = authHeader.substring(7);

            // Verificar que el token no esté vacío
            if (token.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "error", "Token inválido",
                                "status", "error"
                        ));
            }

            // Agregar a blacklist
            tokenBlacklistService.blacklistToken(token);

            return ResponseEntity.ok()
                    .body(Map.of(
                            "message", "Sesión cerrada exitosamente",
                            "status", "success",
                            "blacklistSize", tokenBlacklistService.getBlacklistSize()
                    ));

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Error al procesar logout",
                            "status", "error"
                    ));
        }
    }

    // ── Forgot password ───────────────────────────────────────────────────────
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email requerido."));
        }

        // Siempre responder igual para no revelar si el email existe
        userRepository.findByEmail(email.trim()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            userRepository.save(user);
            try {
                emailService.sendPasswordResetEmail(user.getEmail(), token);
            } catch (Exception e) {

            }
        });

        return ResponseEntity.ok(Map.of("message", "Si el email está registrado, recibirás el enlace en breve."));
    }

    // ── Reset password ────────────────────────────────────────────────────────
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token       = body.get("token");
        String newPassword = body.get("newPassword");

        if (token == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Datos inválidos."));
        }

        if (!newPassword.matches("(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d@!_-]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número. Solo se permiten letras, números y los caracteres @ ! - _"));
        }

        User user = userRepository.findByResetPasswordToken(token).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "El enlace es inválido o ya fue utilizado."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Contraseña restablecida correctamente."));
    }
}