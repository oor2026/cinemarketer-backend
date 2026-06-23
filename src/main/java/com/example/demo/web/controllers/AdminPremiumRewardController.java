package com.example.demo.web.controllers;

import com.example.demo.application.services.PremiumRewardService;
import com.example.demo.domain.premium.*;
import com.example.demo.domain.reward.RewardImage;
import com.example.demo.domain.reward.RewardImageRepository;
import com.example.demo.domain.support.SupportMessage;
import com.example.demo.domain.support.SupportTicket;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.example.demo.application.services.CloudinaryService;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/premium/rewards")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminPremiumRewardController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminPremiumRewardController.class);
    private final PremiumRewardRepository  premiumRewardRepository;
    private final PremiumRewardService     premiumRewardService;
    private final PremiumDrawEntryRepository drawEntryRepository;
    private final CloudinaryService        cloudinaryService;
    private final RewardImageRepository    rewardImageRepository;
    private final com.example.demo.domain.user.UserRepository userRepository;
    private final com.example.demo.application.services.NotificationService notificationService;
    private final com.example.demo.domain.premium.DrawResultRepository drawResultRepository;
    private final com.example.demo.application.services.EmailService emailService;
    private final com.example.demo.domain.support.SupportTicketRepository supportTicketRepository;
    private final com.example.demo.domain.support.SupportMessageRepository supportMessageRepository;

    public AdminPremiumRewardController(PremiumRewardRepository premiumRewardRepository,
                                        PremiumRewardService premiumRewardService,
                                        PremiumDrawEntryRepository drawEntryRepository,
                                        CloudinaryService cloudinaryService,
                                        RewardImageRepository rewardImageRepository,
                                        com.example.demo.domain.user.UserRepository userRepository,
                                        com.example.demo.application.services.NotificationService notificationService, DrawResultRepository drawResultRepository, com.example.demo.application.services.EmailService emailService, com.example.demo.domain.support.SupportTicketRepository supportTicketRepository, com.example.demo.domain.support.SupportMessageRepository supportMessageRepository) {
        this.premiumRewardRepository = premiumRewardRepository;
        this.premiumRewardService    = premiumRewardService;
        this.drawEntryRepository     = drawEntryRepository;
        this.cloudinaryService       = cloudinaryService;
        this.rewardImageRepository   = rewardImageRepository;
        this.userRepository          = userRepository;
        this.notificationService     = notificationService;
        this.drawResultRepository = drawResultRepository;
        this.emailService = emailService;
        this.supportTicketRepository = supportTicketRepository;
        this.supportMessageRepository = supportMessageRepository;
    }

    // =============================================
    // CRUD PREMIOS PREMIUM
    // =============================================

    @GetMapping
    public ResponseEntity<List<PremiumReward>> getAll() {
        return ResponseEntity.ok(premiumRewardRepository.findByDeletedFalse());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return premiumRewardRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            PremiumReward reward = new PremiumReward();
            reward.setName((String) body.get("name"));
            reward.setDescription((String) body.get("description"));
            reward.setImageUrl((String) body.get("imageUrl"));
            reward.setType(PremiumRewardType.valueOf((String) body.get("type")));
            reward.setPartner((String) body.get("partner"));
            reward.setWebsite((String) body.get("website"));
            reward.setTermsConditions((String) body.get("termsConditions"));
            reward.setPointsRequired(body.get("pointsRequired") != null
                    ? ((Number) body.get("pointsRequired")).intValue() : 0);
            reward.setStock(body.get("stock") != null
                    ? ((Number) body.get("stock")).intValue() : null);
            if (body.get("drawDate") != null) {
                reward.setDrawDate(LocalDateTime.parse((String) body.get("drawDate")));
            }
            if (body.get("discountValue") != null)
                reward.setDiscountValue(new java.math.BigDecimal(body.get("discountValue").toString()));
            if (body.get("discountType") != null)
                reward.setDiscountType((String) body.get("discountType"));
            if (body.get("experienceType") != null)
                reward.setExperienceType((String) body.get("experienceType"));
            if (body.get("location") != null)
                reward.setLocation((String) body.get("location"));
            if (body.get("eventDate") != null)
                reward.setEventDate(LocalDateTime.parse((String) body.get("eventDate")));
            if (body.get("maxCapacity") != null)
                reward.setMaxCapacity(((Number) body.get("maxCapacity")).intValue());
            if ("DESCUENTO".equals(body.get("type"))) {
                reward.setDiscountCode(generarCodigoDescuento());
            }

            // Merchandising
            reward.setBrand((String) body.get("brand"));
            reward.setMaterial((String) body.get("material"));
            reward.setColor((String) body.get("color"));
            reward.setSize((String) body.get("size"));
            reward.setDimensions((String) body.get("dimensions"));
            reward.setWeight((String) body.get("weight"));
            reward.setOrigin((String) body.get("origin"));
            reward.setUnitsIncluded((String) body.get("unitsIncluded"));
            reward.setCondition((String) body.get("condition"));

            // Entrada de cine
            reward.setCinemaChain((String) body.get("cinemaChain"));
            reward.setCinemaFormat((String) body.get("cinemaFormat"));
            reward.setCinemaRestrictions((String) body.get("cinemaRestrictions"));
            if (body.get("ticketsIncluded") != null)
                reward.setTicketsIncluded(((Number) body.get("ticketsIncluded")).intValue());
            if (body.get("includesSnack") != null)
                reward.setIncludesSnack((Boolean) body.get("includesSnack"));

            // Descuento
            reward.setDiscountChannel((String) body.get("discountChannel"));
            if (body.get("minimumPurchase") != null)
                reward.setMinimumPurchase(new java.math.BigDecimal(body.get("minimumPurchase").toString()));
            reward.setApplicableProducts((String) body.get("applicableProducts"));
            if (body.get("stackable") != null)
                reward.setStackable((Boolean) body.get("stackable"));

            // Experiencia
            reward.setDuration((String) body.get("duration"));
            if (body.get("includesTransport") != null)
                reward.setIncludesTransport((Boolean) body.get("includesTransport"));
            reward.setRequirements((String) body.get("requirements"));
            if (body.get("companionAllowed") != null)
                reward.setCompanionAllowed((Boolean) body.get("companionAllowed"));

            reward.setActive(true);
            PremiumReward saved = premiumRewardRepository.save(reward);

            // Notificar a todos los usuarios activos discriminando premium vs no premium
            try {
                String tipo = saved.getType().name();
                int puntos = saved.getPointsRequired();
                String nombre = saved.getName();
                List<com.example.demo.domain.user.User> usuarios =
                        userRepository.findByActiveTrueAndSuspendedFalse();
                for (com.example.demo.domain.user.User u : usuarios) {
                    notificationService.crearNuevoPremiumReward(u, nombre, puntos, tipo, u.isActivePremium());
                }
            } catch (Exception ex) {
                // No fallar la creación del premio si falla el envío de notificaciones
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        try {
            PremiumReward reward = opt.get();
            if (body.get("name") != null)             reward.setName((String) body.get("name"));
            if (body.get("description") != null)      reward.setDescription((String) body.get("description"));
            if (body.get("imageUrl") != null)         reward.setImageUrl((String) body.get("imageUrl"));
            if (body.get("partner") != null)          reward.setPartner((String) body.get("partner"));
            if (body.get("website") != null)          reward.setWebsite((String) body.get("website"));
            if (body.get("termsConditions") != null)  reward.setTermsConditions((String) body.get("termsConditions"));
            if (body.get("pointsRequired") != null)
                reward.setPointsRequired(((Number) body.get("pointsRequired")).intValue());
            if (body.get("stock") != null)
                reward.setStock(((Number) body.get("stock")).intValue());
            if (body.get("drawDate") != null)
                reward.setDrawDate(LocalDateTime.parse((String) body.get("drawDate")));
            if (body.get("active") != null)
                reward.setActive((Boolean) body.get("active"));
            if (body.containsKey("discountValue") && body.get("discountValue") != null)
                reward.setDiscountValue(new java.math.BigDecimal(body.get("discountValue").toString()));
            if (body.containsKey("discountType"))
                reward.setDiscountType((String) body.get("discountType"));
            if (body.containsKey("experienceType"))
                reward.setExperienceType((String) body.get("experienceType"));
            if (body.containsKey("location"))
                reward.setLocation((String) body.get("location"));
            if (body.containsKey("eventDate") && body.get("eventDate") != null)
                reward.setEventDate(LocalDateTime.parse((String) body.get("eventDate")));
            if (body.containsKey("maxCapacity"))
                reward.setMaxCapacity(body.get("maxCapacity") != null ? ((Number) body.get("maxCapacity")).intValue() : null);

            // Merchandising
            if (body.containsKey("brand"))          reward.setBrand((String) body.get("brand"));
            if (body.containsKey("material"))       reward.setMaterial((String) body.get("material"));
            if (body.containsKey("color"))          reward.setColor((String) body.get("color"));
            if (body.containsKey("size"))           reward.setSize((String) body.get("size"));
            if (body.containsKey("dimensions"))     reward.setDimensions((String) body.get("dimensions"));
            if (body.containsKey("weight"))         reward.setWeight((String) body.get("weight"));
            if (body.containsKey("origin"))         reward.setOrigin((String) body.get("origin"));
            if (body.containsKey("unitsIncluded"))  reward.setUnitsIncluded((String) body.get("unitsIncluded"));
            if (body.containsKey("condition"))      reward.setCondition((String) body.get("condition"));

            // Entrada de cine
            if (body.containsKey("cinemaChain"))        reward.setCinemaChain((String) body.get("cinemaChain"));
            if (body.containsKey("cinemaFormat"))       reward.setCinemaFormat((String) body.get("cinemaFormat"));
            if (body.containsKey("cinemaRestrictions")) reward.setCinemaRestrictions((String) body.get("cinemaRestrictions"));
            if (body.containsKey("ticketsIncluded") && body.get("ticketsIncluded") != null)
                reward.setTicketsIncluded(((Number) body.get("ticketsIncluded")).intValue());
            if (body.containsKey("includesSnack") && body.get("includesSnack") != null)
                reward.setIncludesSnack((Boolean) body.get("includesSnack"));

            // Descuento
            if (body.containsKey("discountChannel"))     reward.setDiscountChannel((String) body.get("discountChannel"));
            if (body.containsKey("minimumPurchase") && body.get("minimumPurchase") != null)
                reward.setMinimumPurchase(new java.math.BigDecimal(body.get("minimumPurchase").toString()));
            if (body.containsKey("applicableProducts"))  reward.setApplicableProducts((String) body.get("applicableProducts"));
            if (body.containsKey("stackable") && body.get("stackable") != null)
                reward.setStackable((Boolean) body.get("stackable"));

            // Experiencia
            if (body.containsKey("duration"))          reward.setDuration((String) body.get("duration"));
            if (body.containsKey("includesTransport") && body.get("includesTransport") != null)
                reward.setIncludesTransport((Boolean) body.get("includesTransport"));
            if (body.containsKey("requirements"))      reward.setRequirements((String) body.get("requirements"));
            if (body.containsKey("companionAllowed") && body.get("companionAllowed") != null)
                reward.setCompanionAllowed((Boolean) body.get("companionAllowed"));

            return ResponseEntity.ok(premiumRewardRepository.save(reward));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PremiumReward reward = opt.get();
        reward.setActive(false);
        premiumRewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio desactivado correctamente"));
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PremiumReward reward = opt.get();
        reward.setDeleted(true);
        premiumRewardRepository.save(reward);
        return ResponseEntity.ok(Map.of("message", "Premio eliminado correctamente"));
    }

    // =============================================
    // SORTEOS
    // =============================================

    @GetMapping("/{id}/entries")
    public ResponseEntity<?> getEntries(@PathVariable Long id) {
        List<PremiumDrawEntry> entries = drawEntryRepository.findByRewardId(id);
        return ResponseEntity.ok(Map.of(
                "rewardId",     id,
                "totalEntries", entries.size(),
                "entries", entries.stream().map(e -> Map.of(
                        "userId",    e.getUser().getId(),
                        "userName",  e.getUser().getName(),
                        "userEmail", e.getUser().getEmail(),
                        "enteredAt", e.getEnteredAt()
                )).toList()
        ));
    }

    @PostMapping("/{id}/draw")
    public ResponseEntity<?> executeDraw(@PathVariable Long id) {
        Optional<PremiumReward> opt = premiumRewardRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PremiumReward reward = opt.get();
        if (reward.getType() != PremiumRewardType.SORTEO) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Este premio no es un sorteo"));
        }
        if (reward.isDrawExecuted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El sorteo ya fue ejecutado"));
        }
        try {
            return ResponseEntity.ok(premiumRewardService.executeDraw(reward));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // =============================================
    // GESTIÓN DE IMÁGENES
    // =============================================

    /**
     * Listar imágenes de un premio premium
     * GET /api/admin/premium/rewards/{id}/images
     */
    @GetMapping("/{id}/images")
    public ResponseEntity<?> getImages(@PathVariable Long id) {
        List<RewardImage> images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "PREMIUM");
        return ResponseEntity.ok(images);
    }

    /**
     * Subir nueva imagen a un premio premium (máx 5)
     * POST /api/admin/premium/rewards/{id}/images
     */
    @PostMapping("/{id}/images")
    @Transactional
    public ResponseEntity<?> addImage(@PathVariable Long id,
                                      @RequestParam("image") MultipartFile file) {
        PremiumReward reward = premiumRewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

        long count = rewardImageRepository.countByRewardIdAndRewardType(id, "PREMIUM");
        if (count >= 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Máximo 5 imágenes por premio"));
        }

        try {
            String imageUrl = cloudinaryService.uploadImage(file, "cinemarketer/rewards/premium");

            RewardImage img = new RewardImage();
            img.setRewardId(id);
            img.setRewardType("PREMIUM");
            img.setImageUrl(imageUrl);
            img.setPrimary(count == 0);
            rewardImageRepository.save(img);

            if (count == 0) {
                reward.setImageUrl(imageUrl);
                premiumRewardRepository.save(reward);
            }

            return ResponseEntity.ok(Map.of(
                    "id",        img.getId(),
                    "imageUrl",  imageUrl,
                    "isPrimary", img.isPrimary()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al subir imagen: " + e.getMessage()));
        }
    }

    /**
     * Marcar imagen como principal
     * PATCH /api/admin/premium/rewards/{id}/images/{imageId}/primary
     */
    @PatchMapping("/{id}/images/{imageId}/primary")
    @Transactional
    public ResponseEntity<?> setPrimaryImage(@PathVariable Long id,
                                             @PathVariable Long imageId) {
        List<RewardImage> images = rewardImageRepository
                .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "PREMIUM");
        images.forEach(img -> img.setPrimary(false));
        rewardImageRepository.saveAll(images);

        RewardImage img = rewardImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Imagen no encontrada"));
        img.setPrimary(true);
        rewardImageRepository.save(img);

        PremiumReward reward = premiumRewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Premio no encontrado"));
        reward.setImageUrl(img.getImageUrl());
        premiumRewardRepository.save(reward);

        return ResponseEntity.ok(Map.of("message", "Imagen principal actualizada"));
    }

    /**
     * Eliminar imagen individual
     * DELETE /api/admin/premium/rewards/{id}/images/{imageId}
     */
    @DeleteMapping("/{id}/images/{imageId}")
    @Transactional
    public ResponseEntity<?> deleteImage(@PathVariable Long id,
                                         @PathVariable Long imageId) {
        RewardImage img = rewardImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Imagen no encontrada"));

        try { cloudinaryService.deleteImage(img.getImageUrl()); } catch (Exception ignored) {}

        boolean eraPrincipal = img.isPrimary();
        rewardImageRepository.delete(img);

        if (eraPrincipal) {
            List<RewardImage> restantes = rewardImageRepository
                    .findByRewardIdAndRewardTypeOrderByPrimaryDesc(id, "PREMIUM");
            PremiumReward reward = premiumRewardRepository.findById(id).orElse(null);
            if (!restantes.isEmpty()) {
                restantes.get(0).setPrimary(true);
                rewardImageRepository.save(restantes.get(0));
                if (reward != null) {
                    reward.setImageUrl(restantes.get(0).getImageUrl());
                    premiumRewardRepository.save(reward);
                }
            } else if (reward != null) {
                reward.setImageUrl(null);
                premiumRewardRepository.save(reward);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Imagen eliminada correctamente"));
    }

    /**
     * Endpoint legacy — subir imagen única
     * POST /api/admin/premium/rewards/{id}/image
     */
    @PostMapping("/{id}/image")
    @Transactional
    public ResponseEntity<?> uploadImageLegacy(@PathVariable Long id,
                                               @RequestParam("image") MultipartFile file) {
        return addImage(id, file);
    }

    // =============================================
    // HELPERS
    // =============================================

    private String generarCodigoDescuento() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder("CINE-");
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    // POST /admin/premium/rewards/{id}/draw/descalificar/{position}
    @PostMapping("/{id}/draw/descalificar/{position}")
    @Transactional
    public ResponseEntity<?> descalificarSeleccionado(
            @PathVariable Long id,
            @PathVariable int position) {
        try {
            PremiumReward reward = premiumRewardRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Sorteo no encontrado"));

            if (!reward.isDrawExecuted()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El sorteo aún no fue ejecutado"));
            }

            // Buscar y descalificar la posición indicada
            List<com.example.demo.domain.premium.DrawResult> results =
                    drawResultRepository.findByRewardIdOrderByPosition(reward.getId());

            com.example.demo.domain.premium.DrawResult aDescalificar = results.stream()
                    .filter(r -> r.getPosition() == position)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Posición no encontrada"));

            // Cambiar posición del descalificado a negativo para liberar el slot
            aDescalificar.setStatus("DESCALIFICADO");
            aDescalificar.setPosition(-position);
            drawResultRepository.save(aDescalificar);

            // Ascender al siguiente activo
            com.example.demo.domain.premium.DrawResult nuevoGanador = results.stream()
                    .filter(r -> r.getPosition() > position && "ACTIVO".equals(r.getStatus()))
                    .findFirst()
                    .orElse(null);

            // Notificar al descalificado
            try {
                notificationService.crearNotifDescalificado(
                        aDescalificar.getUser(), reward.getName());
                emailService.sendDrawDescalificadoEmail(
                        aDescalificar.getUser().getEmail(),
                        aDescalificar.getUser().getName(),
                        reward.getName());
            } catch (Exception e) {
                log.warn("No se pudo notificar al descalificado: {}", e.getMessage());
            }

            if (nuevoGanador != null) {
                nuevoGanador.setPosition(1);
                drawResultRepository.save(nuevoGanador);

                reward.setWinner(nuevoGanador.getUser());
                premiumRewardRepository.save(reward);

                // Notificar al nuevo ganador
                try {
                    notificationService.crearNotifNuevoGanador(
                            nuevoGanador.getUser(), reward.getName());
                    emailService.sendDrawWinnerSustitutoEmail(
                            nuevoGanador.getUser().getEmail(),
                            nuevoGanador.getUser().getName(),
                            reward.getName());

                    // Ticket de soporte para coordinar entrega
                    SupportTicket ticket = new SupportTicket();
                    ticket.setUser(nuevoGanador.getUser());
                    ticket.setSubject("¡Ganaste el sorteo: " + reward.getName() + "!");
                    ticket.setStatus(com.example.demo.domain.support.TicketStatus.OPEN);
                    SupportTicket savedTicket = supportTicketRepository.save(ticket);

                    SupportMessage message = new SupportMessage();
                    message.setTicket(savedTicket);
                    message.setSenderType(com.example.demo.domain.support.SenderType.ADMIN);
                    message.setSenderName("Cinemarketer");
                    message.setContent("¡Felicitaciones " + nuevoGanador.getUser().getName() + "!\n\n" +
                            "El ganador original del sorteo \"" + reward.getName() + "\" no pudo coordinar la entrega, " +
                            "por lo que fuiste seleccionado/a como nuevo ganador/a.\n\n" +
                            "Nuestro equipo se pondrá en contacto con vos a la brevedad para coordinar la entrega del premio.\n\n" +
                            "Equipo Cinemarketer.");
                    message.setReadByAdmin(true);
                    message.setReadByUser(false);
                    supportMessageRepository.save(message);
                } catch (Exception e) {
                    log.warn("No se pudo notificar al nuevo ganador: {}", e.getMessage());
                }

                return ResponseEntity.ok(Map.of(
                        "nuevoGanadorName", nuevoGanador.getUser().getName(),
                        "nuevoGanadorEmail", nuevoGanador.getUser().getEmail()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "nuevoGanadorName", "Sin suplentes disponibles",
                        "nuevoGanadorEmail", ""
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/draw/results")
    public ResponseEntity<?> getDrawResults(@PathVariable Long id) {
        PremiumReward reward = premiumRewardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sorteo no encontrado"));

        if (!reward.isDrawExecuted()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El sorteo aún no fue ejecutado"));
        }

        List<com.example.demo.domain.premium.DrawResult> results =
                drawResultRepository.findByRewardIdOrderByPosition(reward.getId());

        // Armar respuesta en el mismo formato que executeDraw
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("rewardId", reward.getId());
        response.put("rewardName", reward.getName());
        response.put("totalParticipants", drawEntryRepository.countByRewardId(reward.getId()));
        response.put("elegibles", results.size());

        for (com.example.demo.domain.premium.DrawResult dr : results) {
            if (dr.getPosition() == 1 && "ACTIVO".equals(dr.getStatus())) {
                response.put("winnerId", dr.getUser().getId());
                response.put("winnerName", dr.getUser().getName());
                response.put("winnerEmail", dr.getUser().getEmail());
            } else if (dr.getPosition() == 2 && "ACTIVO".equals(dr.getStatus())) {
                response.put("suplente1Id", dr.getUser().getId());
                response.put("suplente1Name", dr.getUser().getName());
            } else if (dr.getPosition() == 3 && "ACTIVO".equals(dr.getStatus())) {
                response.put("suplente2Id", dr.getUser().getId());
                response.put("suplente2Name", dr.getUser().getName());
            }
        }

        return ResponseEntity.ok(response);
    }
}