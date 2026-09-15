package com.example.demo.web.controllers;

import com.example.demo.application.services.EmailService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.infrastructure.security.CustomUserDetailsService;
import com.example.demo.infrastructure.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/self-service")
public class SelfServiceController {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final com.example.demo.domain.redemption.RedemptionRepository redemptionRepository;
    private final com.example.demo.domain.redemption.RedemptionDeliveryPointRepository deliveryPointRepository;

    private final com.example.demo.domain.premium.PremiumRedemptionRepository premiumRedemptionRepository;
    private final com.example.demo.domain.premium.PremiumRedemptionDeliveryPointRepository premiumDeliveryPointRepository;

    public SelfServiceController(UserRepository userRepository,
                                 EmailService emailService,
                                 JwtService jwtService,
                                 CustomUserDetailsService userDetailsService,
                                 com.example.demo.domain.redemption.RedemptionRepository redemptionRepository,
                                 com.example.demo.domain.redemption.RedemptionDeliveryPointRepository deliveryPointRepository,
                                 com.example.demo.domain.premium.PremiumRedemptionRepository premiumRedemptionRepository,
                                 com.example.demo.domain.premium.PremiumRedemptionDeliveryPointRepository premiumDeliveryPointRepository, com.example.demo.domain.premium.PremiumRedemptionRepository premiumRedemptionRepository1, com.example.demo.domain.premium.PremiumRedemptionDeliveryPointRepository premiumDeliveryPointRepository1) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.redemptionRepository = redemptionRepository;
        this.deliveryPointRepository = deliveryPointRepository;
        this.premiumRedemptionRepository = premiumRedemptionRepository1;
        this.premiumDeliveryPointRepository = premiumDeliveryPointRepository1;
    }

    @PostMapping("/request-link")
    public ResponseEntity<?> requestSelfServiceLink(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email requerido."));
        }

        User user = userRepository.findByEmail(email.trim()).orElse(null);

        // Mismo criterio que forgot-password: responder siempre igual,
        // exista o no el mail, para no permitir usar esta pantalla como
        // forma de averiguar qué mails están registrados en Cinemarketer.
        if (user != null && !user.isDemo()) {
            String token = UUID.randomUUID().toString();
            user.setSelfServiceToken(token);
            user.setSelfServiceTokenExpiresAt(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);
            try {
                emailService.sendSelfServiceLoginEmail(user.getEmail(), token);
            } catch (Exception e) {}
        }

        return ResponseEntity.ok(Map.of("message", "Si el email está registrado, recibirás el enlace en breve."));
    }

    // El link del mail apunta acá. Mismo patrón de dos pasos que
    // reset-password-redirect + reset-password: este GET solo valida
    // y redirige al frontend con el token en la URL — el intercambio
    // real por el JWT lo hace el POST /exchange de abajo, para que el
    // JWT nunca viaje pegado en una URL ni quede en el historial del navegador.
    @GetMapping("/verify-redirect")
    public ResponseEntity<Void> verifyRedirect(@RequestParam String token) {
        User user = userRepository.findBySelfServiceToken(token).orElse(null);

        String redirectUrl;
        if (user == null) {
            redirectUrl = frontendUrl + "/centro-autogestion.html?error=invalid";
        } else if (user.getSelfServiceTokenExpiresAt() == null
                || user.getSelfServiceTokenExpiresAt().isBefore(LocalDateTime.now())) {
            redirectUrl = frontendUrl + "/centro-autogestion.html?error=expired";
        } else {
            redirectUrl = frontendUrl + "/centro-autogestion.html?token=" + token;
        }

        return ResponseEntity.status(HttpStatus.FOUND).header("Location", redirectUrl).build();
    }

    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token requerido."));
        }

        User user = userRepository.findBySelfServiceToken(token).orElse(null);
        if (user == null || user.getSelfServiceTokenExpiresAt() == null
                || user.getSelfServiceTokenExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "El enlace es inválido o expiró. Pedí uno nuevo."));
        }

        // Un solo uso — se invalida apenas se canjea por el JWT.
        user.setSelfServiceToken(null);
        user.setSelfServiceTokenExpiresAt(null);
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String jwt = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(Map.of("token", jwt));
    }

    // ==============================================
    // PERFIL — completar datos faltantes
    // ==============================================

    @GetMapping("/perfil-pendiente")
    public ResponseEntity<?> perfilPendiente(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);

        Map<String, Boolean> faltantes = new java.util.HashMap<>();
        faltantes.put("birthDate", user.getBirthDate() == null);
        faltantes.put("sexo", user.getSexo() == null || user.getSexo().isBlank());
        faltantes.put("provincia", user.getProvincia() == null || user.getProvincia().isBlank());
        faltantes.put("localidad", user.getLocalidad() == null || user.getLocalidad().isBlank());

        boolean completo = faltantes.values().stream().noneMatch(Boolean::booleanValue);
        return ResponseEntity.ok(Map.of("completo", completo, "faltantes", faltantes));
    }

    @PatchMapping("/perfil")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> completarPerfil(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody Map<String, String> body) {
        User user = getUser(userDetails);

        if (body.get("birthDate") != null && !body.get("birthDate").isBlank()) {
            user.setBirthDate(java.time.LocalDate.parse(body.get("birthDate")));
        }
        if (body.get("sexo") != null && !body.get("sexo").isBlank()) {
            user.setSexo(body.get("sexo"));
        }
        if (body.get("provincia") != null && !body.get("provincia").isBlank()) {
            user.setProvincia(body.get("provincia"));
        }
        if (body.get("localidad") != null && !body.get("localidad").isBlank()) {
            user.setLocalidad(body.get("localidad"));
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Perfil actualizado correctamente."));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    // ==============================================
    // ELEGIR PUNTO DE ENTREGA — pasa el canje a COORDINATED
    // ==============================================

    @PostMapping("/canjes/{id}/elegir-punto")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> elegirPuntoFree(@PathVariable Long id,
                                             @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody Map<String, Object> body) {
        User user = getUser(userDetails);

        com.example.demo.domain.redemption.Redemption redemption = redemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje no encontrado"));

        // El canje tiene que ser del usuario logueado — nadie puede
        // coordinar la entrega de un premio ajeno con su propio JWT.
        if (!redemption.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Este canje no te pertenece."));
        }

        String deliveryMethod = redemption.getReward().getDeliveryMethod();

        if ("ENVIO_DOMICILIO".equals(deliveryMethod)) {
            String address = (String) body.get("address");
            if (address == null || address.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Falta la dirección de envío."));
            }
            redemption.setDeliveryAddress(address);
        } else {
            Long deliveryPointId = body.get("deliveryPointId") != null
                    ? Long.valueOf(body.get("deliveryPointId").toString()) : null;
            if (deliveryPointId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Falta elegir un punto de entrega."));
            }
            com.example.demo.domain.redemption.RedemptionDeliveryPoint punto = deliveryPointRepository.findById(deliveryPointId)
                    .orElseThrow(() -> new RuntimeException("Punto de entrega no encontrado"));

            // El punto tiene que pertenecer a ESTE canje — evita que alguien
            // elija un punto cargado para el canje de otra persona.
            if (!punto.getRedemption().getId().equals(redemption.getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Ese punto no corresponde a este canje."));
            }
            redemption.setChosenDeliveryPoint(punto);
        }

        redemption.setStatus(com.example.demo.domain.redemption.RedemptionStatus.COORDINATED);
        redemptionRepository.save(redemption);

        return ResponseEntity.ok(Map.of("message", "¡Listo! Coordinaste la entrega de tu premio."));
    }

    @PostMapping("/canjes-premium/{id}/elegir-punto")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> elegirPuntoPremium(@PathVariable Long id,
                                                @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails userDetails,
                                                @RequestBody Map<String, Object> body) {
        User user = getUser(userDetails);

        com.example.demo.domain.premium.PremiumRedemption redemption = premiumRedemptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Canje premium no encontrado"));

        if (!redemption.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Este canje no te pertenece."));
        }

        String deliveryMethod = redemption.getReward().getDeliveryMethod();

        if ("ENVIO_DOMICILIO".equals(deliveryMethod)) {
            String address = (String) body.get("address");
            if (address == null || address.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Falta la dirección de envío."));
            }
            redemption.setDeliveryAddress(address);
        } else {
            Long deliveryPointId = body.get("deliveryPointId") != null
                    ? Long.valueOf(body.get("deliveryPointId").toString()) : null;
            if (deliveryPointId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Falta elegir un punto de entrega."));
            }
            com.example.demo.domain.premium.PremiumRedemptionDeliveryPoint punto = premiumDeliveryPointRepository.findById(deliveryPointId)
                    .orElseThrow(() -> new RuntimeException("Punto de entrega no encontrado"));

            if (!punto.getRedemption().getId().equals(redemption.getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Ese punto no corresponde a este canje."));
            }
            redemption.setChosenDeliveryPoint(punto);
        }

        redemption.setStatus(com.example.demo.domain.premium.PremiumRedemptionStatus.COORDINATED);
        premiumRedemptionRepository.save(redemption);

        return ResponseEntity.ok(Map.of("message", "¡Listo! Coordinaste la entrega de tu premio."));
    }

    @Value("${app.self-service.whatsapp:}")
    private String whatsappSupportPhone;

    @GetMapping("/canjes")
    public ResponseEntity<List<com.example.demo.application.dtos.SelfServiceRedemptionDto>> misCanjes(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);

        List<com.example.demo.domain.redemption.RedemptionStatus> estadosFree = List.of(
                com.example.demo.domain.redemption.RedemptionStatus.PENDING,
                com.example.demo.domain.redemption.RedemptionStatus.COORDINATED
        );
        List<com.example.demo.domain.premium.PremiumRedemptionStatus> estadosPremium = List.of(
                com.example.demo.domain.premium.PremiumRedemptionStatus.PENDING,
                com.example.demo.domain.premium.PremiumRedemptionStatus.COORDINATED
        );

        List<com.example.demo.application.dtos.SelfServiceRedemptionDto> resultado = new java.util.ArrayList<>();

        for (var r : redemptionRepository.findByUserIdAndStatusIn(user.getId(), estadosFree)) {
            resultado.add(toSelfServiceDto(r));
        }
        for (var r : premiumRedemptionRepository.findByUserIdAndStatusIn(user.getId(), estadosPremium)) {
            resultado.add(toSelfServiceDto(r));
        }

        return ResponseEntity.ok(resultado);
    }

    private com.example.demo.application.dtos.SelfServiceRedemptionDto toSelfServiceDto(com.example.demo.domain.redemption.Redemption r) {
        var dto = new com.example.demo.application.dtos.SelfServiceRedemptionDto();
        dto.setId(r.getId());
        dto.setRedemptionType("FREE");
        dto.setRewardName(r.getReward().getName());
        dto.setRewardImageUrl(r.getReward().getImageUrl());
        dto.setStatus(r.getStatus().name());
        dto.setDeliveryMethod(r.getReward().getDeliveryMethod());
        dto.setWhatsappSupportPhone(whatsappSupportPhone);
        dto.setDeliveryAddress(r.getDeliveryAddress());

        if (r.getStatus() == com.example.demo.domain.redemption.RedemptionStatus.COORDINATED) {
            if (r.getChosenDeliveryPoint() != null) {
                dto.setChosenDeliveryPoint(toPointDto(r.getChosenDeliveryPoint()));
            }
        } else {
            dto.setDeliveryPoints(deliveryPointRepository.findByRedemptionIdOrderByDisplayOrderAsc(r.getId())
                    .stream().map(this::toPointDto).collect(java.util.stream.Collectors.toList()));
        }

        return dto;
    }

    private com.example.demo.application.dtos.SelfServiceRedemptionDto toSelfServiceDto(com.example.demo.domain.premium.PremiumRedemption r) {
        var dto = new com.example.demo.application.dtos.SelfServiceRedemptionDto();
        dto.setId(r.getId());
        dto.setRedemptionType("PREMIUM");
        dto.setRewardName(r.getReward().getName());
        dto.setRewardImageUrl(r.getReward().getImageUrl());
        dto.setStatus(r.getStatus().name());
        dto.setDeliveryMethod(r.getReward().getDeliveryMethod());
        dto.setWhatsappSupportPhone(whatsappSupportPhone);
        dto.setDeliveryAddress(r.getDeliveryAddress());

        if (r.getStatus() == com.example.demo.domain.premium.PremiumRedemptionStatus.COORDINATED) {
            if (r.getChosenDeliveryPoint() != null) {
                dto.setChosenDeliveryPoint(toPointDto(r.getChosenDeliveryPoint()));
            }
        } else {
            dto.setDeliveryPoints(premiumDeliveryPointRepository.findByRedemptionIdOrderByDisplayOrderAsc(r.getId())
                    .stream().map(this::toPointDto).collect(java.util.stream.Collectors.toList()));
        }

        return dto;
    }

    private com.example.demo.application.dtos.DeliveryPointDto toPointDto(com.example.demo.domain.redemption.RedemptionDeliveryPoint p) {
        return new com.example.demo.application.dtos.DeliveryPointDto(p.getId(), p.getLocationReference(), p.getScheduleInfo(), p.getDisplayOrder());
    }

    private com.example.demo.application.dtos.DeliveryPointDto toPointDto(com.example.demo.domain.premium.PremiumRedemptionDeliveryPoint p) {
        return new com.example.demo.application.dtos.DeliveryPointDto(p.getId(), p.getLocationReference(), p.getScheduleInfo(), p.getDisplayOrder());
    }
}