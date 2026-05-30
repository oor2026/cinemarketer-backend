package com.example.demo.application.services;

import com.example.demo.application.dtos.CompleteProfileRequestDto;
import com.example.demo.application.dtos.GoogleAuthRequestDto;
import com.example.demo.application.dtos.LoginResponse;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.user.UserRole;
import com.example.demo.infrastructure.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Optional;

@Service
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SubscriptionService subscriptionService;

    @Value("${google.client.id}")
    private String googleClientId;

    public GoogleAuthService(
            UserRepository userRepository,
            JwtService jwtService,
            UserDetailsService userDetailsService,
            SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.subscriptionService = subscriptionService;
    }

    public LoginResponse authenticateWithGoogle(GoogleAuthRequestDto request) {
        // 1. Verificar el token con Google
        GoogleIdToken.Payload payload = verifyGoogleToken(request.getCredential());

        String googleId = payload.getSubject();
        String email    = payload.getEmail();
        String name     = (String) payload.get("name");
        String picture  = (String) payload.get("picture");

        // 2. Buscar usuario existente por googleId o email
        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> userRepository.findByEmail(email).orElse(null));

        if (user == null) {
            // 3a. Usuario nuevo — crear con profileComplete = false
            user = new User();
            user.setName(name != null ? name : email);
            user.setEmail(email);
            user.setGoogleId(googleId);
            user.setProfileImageUrl(picture);
            user.setEmailVerified(true); // Google ya verificó el email
            user.setActive(true);
            user.setRole(UserRole.USER);
            user.setProfileComplete(false); // necesita completar DNI y teléfono
            user.setPassword(null);
            userRepository.save(user);
        } else {
            // 3b. Usuario existente — vincular googleId si no lo tenía
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
                userRepository.save(user);
            }
            // Verificar suspensión
            if (user.isSuspended()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Tu cuenta ha sido suspendida. Contactá a soporte.");
            }
        }

        // 4. Generar JWT — usar el email guardado en la BD, no el de Google
        // (el usuario pudo haber cambiado su email en Mi Cuenta)
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        // 5. Construir respuesta
        boolean isPremium = subscriptionService.isActivePremium(user.getId());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setType("Bearer");
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().name());
        response.setTotalPoints(user.getTotalPoints());
        response.setSuccess(true);
        response.setLevel(user.getLevel());
        response.setPremium(isPremium);
        response.setProfileComplete(user.isProfileComplete());
        return response;
    }

    public LoginResponse completeProfile(CompleteProfileRequestDto request, String authHeader) {
        // 1. Extraer email del JWT
        String token = authHeader.substring(7);
        String email = jwtService.extractUsername(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // 2. Validar DNI
        if (request.getDni() == null || !request.getDni().matches("\\d{7,8}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DNI inválido. Debe tener 7 u 8 dígitos.");
        }

        // 3. Validar que el DNI no esté en uso
        if (userRepository.existsByDni(request.getDni())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este DNI ya se encuentra registrado.");
        }

        // 4. Validar teléfono
        if (request.getPhone() == null || request.getPhone().trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teléfono inválido.");
        }

        // 5. Guardar datos y marcar perfil completo
        user.setDni(request.getDni());
        user.setPhone(request.getPhone());
        user.setProfileComplete(true);
        userRepository.save(user);

        // 6. Devolver nuevo JWT con profileComplete = true
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String newToken = jwtService.generateToken(userDetails);

        boolean isPremium = subscriptionService.isActivePremium(user.getId());

        LoginResponse response = new LoginResponse();
        response.setToken(newToken);
        response.setType("Bearer");
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().name());
        response.setTotalPoints(user.getTotalPoints());
        response.setSuccess(true);
        response.setLevel(user.getLevel());
        response.setPremium(isPremium);
        response.setProfileComplete(true);
        return response;
    }

    private GoogleIdToken.Payload verifyGoogleToken(String credential) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de Google inválido");
            }
            return idToken.getPayload();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Error verificando token de Google");
        }
    }
}