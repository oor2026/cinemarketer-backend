package com.example.demo.application.services;

import com.example.demo.application.dtos.SubscriptionDto;
import com.example.demo.domain.subscription.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final SubscriptionPendingConfirmationRepository pendingConfirmationRepository;
    private final UserRepository userRepository;
    private final MercadoPagoService mercadoPagoService;
    private final EmailService emailService;

    public SubscriptionService(UserSubscriptionRepository userSubscriptionRepository,
                               SubscriptionPlanRepository subscriptionPlanRepository,
                               SubscriptionPaymentRepository subscriptionPaymentRepository,
                               SubscriptionPendingConfirmationRepository pendingConfirmationRepository,
                               UserRepository userRepository,
                               MercadoPagoService mercadoPagoService,
                               EmailService emailService) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.pendingConfirmationRepository = pendingConfirmationRepository;
        this.userRepository = userRepository;
        this.mercadoPagoService = mercadoPagoService;
        this.emailService = emailService;
    }

    // ==============================================
    // VERIFICAR SI EL USUARIO ES PREMIUM ACTIVO
    // ==============================================

    public boolean isActivePremium(Long userId) {
        Optional<UserSubscription> sub = userSubscriptionRepository
                .findByUserIdAndStatusAndPlanName(userId, SubscriptionStatus.ACTIVE, "Premium");
        return sub.isPresent() && sub.get().isActive();
    }

    // ==============================================
    // OBTENER INFO DEL PLAN
    // ==============================================

    public SubscriptionDto getPlanInfo() {
        return getPlanInfo("Premium");
    }

    public SubscriptionDto getPlanInfo(String planName) {
        SubscriptionPlan plan = subscriptionPlanRepository.findByNameAndActiveTrue(planName)
                .orElseThrow(() -> new RuntimeException("No hay plan de suscripción activo: " + planName));

        SubscriptionDto dto = new SubscriptionDto();
        dto.setPlanId(plan.getId());
        dto.setPlanName(plan.getName());
        dto.setPlanType(plan.getType());
        dto.setPlanPrice(plan.getPrice());
        dto.setPointsMultiplier(plan.getPointsMultiplier());
        dto.setBenefits(parseBenefits(plan.getBenefits()));
        dto.setActive(false);
        return dto;
    }

    // ==============================================
    // PROCESAR WEBHOOK — SUSCRIPCIÓN
    // ==============================================

    @Transactional
    public void processWebhookSubscription(String preapprovalId) {
        log.info("📩 Procesando webhook suscripción: {}", preapprovalId);

        try {
            Map<String, Object> mpData = mercadoPagoService.getSubscription(preapprovalId);
            String mpStatus = (String) mpData.get("status");
            String payerEmail = (String) mpData.get("payerEmail");
            String mpPlanId = (String) mpData.get("preapprovalPlanId");

            Optional<UserSubscription> subOpt = userSubscriptionRepository
                    .findByMpPreapprovalId(preapprovalId);

            UserSubscription sub;

            if (subOpt.isEmpty()) {
                if (payerEmail == null || payerEmail.isBlank()) {
                    log.warn("⚠️ No se encontró payer_email para preapprovalId: {}", preapprovalId);
                    return;
                }

                Optional<User> userOpt = userRepository.findByEmail(payerEmail);
                if (userOpt.isEmpty()) {
                    log.warn("⚠️ No se encontró usuario con email: {}", payerEmail);
                    return;
                }

                // Identificar el plan correcto (Premium o Creator) por el
                // preapproval_plan_id real que informó MP — no asumir "el
                // primero que haya", ahora que hay más de un plan activo.
                SubscriptionPlan plan = (mpPlanId != null)
                        ? subscriptionPlanRepository.findByMpPreapprovalPlanId(mpPlanId).orElse(null)
                        : subscriptionPlanRepository.findByNameAndActiveTrue("Premium").orElse(null);

                if (plan == null) {
                    log.warn("⚠️ No se pudo identificar el plan para preapprovalPlanId: {}", mpPlanId);
                    return;
                }

                sub = new UserSubscription();
                sub.setUser(userOpt.get());
                sub.setPlan(plan);
                sub.setMpPreapprovalId(preapprovalId);
                sub.setStatus(SubscriptionStatus.PENDING);
                log.info("📋 Creando nueva suscripción a {} via plan para usuario: {}", plan.getName(), payerEmail);

            } else {
                sub = subOpt.get();
            }

            switch (mpStatus) {
                case "authorized" -> {
                    // "authorized" únicamente confirma que el medio de pago recurrente
                    // quedó habilitado a nivel Mercado Pago — NO significa que se haya
                    // cobrado exitosamente ninguna cuota, ni la primera ni una renovación.
                    // Activar acceso con solo este evento permitía el escenario real que
                    // encontramos: el usuario autoriza el pago recurrente, el primer
                    // cobro queda pending y después rejected, y aun así ya tenía un mes
                    // completo de Premium regalado porque esta rama lo activaba antes
                    // de saber si el cobro iba a salir bien.
                    //
                    // Ahora esta rama NUNCA otorga acceso ni toca fechas — solo
                    // actualiza metadata (fecha de próxima facturación) si la tenemos.
                    // El único lugar que activa/extiende acceso real es
                    // processWebhookPayment, cuando el pago llega efectivamente
                    // "approved".
                    String nextBilling = (String) mpData.get("nextPaymentDate");
                    if (nextBilling != null) {
                        sub.setNextBillingDate(LocalDateTime.parse(nextBilling.substring(0, 19)));
                    }
                    log.info("ℹ️ Suscripción {} autorizada a nivel MP — esperando el pago aprobado real para otorgar acceso.", preapprovalId);
                }
                case "cancelled", "paused" -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    sub.setCancelledAt(LocalDateTime.now());
                    log.info("🚫 Suscripción cancelada para usuario: {}", sub.getUser().getEmail());
                }
                default -> log.debug("Estado MP no manejado: {}", mpStatus);
            }

            userSubscriptionRepository.save(sub);

        } catch (Exception e) {
            log.error("❌ Error procesando webhook suscripción {}: {}", preapprovalId, e.getMessage());
        }
    }

    // ==============================================
    // PROCESAR WEBHOOK — PAGO
    // ==============================================

    @Transactional
    public void processWebhookPayment(String paymentId) {
        log.info("📩 Procesando webhook pago: {}", paymentId);

        // Idempotencia real: solo cortamos si este pago YA estaba aprobado
        // antes — un mismo mp_payment_id puede pasar legítimamente de
        // "pending" a "approved" en un webhook posterior, y eso hay que
        // procesarlo (actualizando la fila existente), no ignorarlo.
        Optional<SubscriptionPayment> pagoExistente = subscriptionPaymentRepository.findByMpPaymentId(paymentId);
        if (pagoExistente.isPresent() && "approved".equals(pagoExistente.get().getStatus())) {
            log.info("↩️ Pago {} ya estaba aprobado, ignorando notificación duplicada.", paymentId);
            return;
        }

        try {
            Map<String, Object> paymentData = mercadoPagoService.getPayment(paymentId);
            String status = (String) paymentData.get("status");
            String preapprovalId = (String) paymentData.get("preapprovalId");

            if (preapprovalId == null) {
                log.warn("⚠️ Pago {} sin preapprovalId", paymentId);
                return;
            }

            Optional<UserSubscription> subOpt = userSubscriptionRepository
                    .findByMpPreapprovalId(preapprovalId);

            UserSubscription sub;

            if (subOpt.isEmpty()) {
                String payerEmail = (String) paymentData.get("payerEmail");
                if (payerEmail == null || payerEmail.isBlank()) {
                    log.warn("⚠️ Pago {} sin preapprovalId en DB ni payerEmail", paymentId);
                    return;
                }

                Optional<User> userOpt = userRepository.findByEmail(payerEmail);
                if (userOpt.isEmpty()) {
                    log.warn("⚠️ No se encontró usuario con email: {}", payerEmail);

                    // Crear confirmación pendiente y enviar emails solo si no existe ya
                    if (!pendingConfirmationRepository.existsByMpPreapprovalId(preapprovalId)) {
                        SubscriptionPendingConfirmation pending = new SubscriptionPendingConfirmation();
                        pending.setToken(UUID.randomUUID().toString());
                        pending.setMpPayerEmail(payerEmail);
                        pending.setMpPreapprovalId(preapprovalId);
                        pending.setMpPaymentId(paymentId);
                        double amount = paymentData.get("transactionAmount") != null
                                ? Double.parseDouble(paymentData.get("transactionAmount").toString()) : 999.0;
                        pending.setAmount(amount);
                        // Guardamos el plan real que informó MP, para que la
                        // confirmación posterior active el plan correcto y no
                        // asuma Premium por defecto.
                        pending.setMpPreapprovalPlanId((String) paymentData.get("preapprovalPlanId"));
                        pendingConfirmationRepository.save(pending);

                        String confirmUrl = "https://cinemarketer.com.ar/api/subscriptions/confirm?token=" + pending.getToken();
                        emailService.sendSubscriptionConfirmationEmail(payerEmail, confirmUrl, amount);
                        emailService.sendAdminSubscriptionAlert(payerEmail, preapprovalId, amount);
                        log.info("📧 Emails enviados para confirmación pendiente: {}", payerEmail);
                    }
                    return;
                }

                String mpPlanIdPago = (String) paymentData.get("preapprovalPlanId");
                SubscriptionPlan plan = (mpPlanIdPago != null)
                        ? subscriptionPlanRepository.findByMpPreapprovalPlanId(mpPlanIdPago).orElse(null)
                        : subscriptionPlanRepository.findByNameAndActiveTrue("Premium").orElse(null);
                if (plan == null) return;

                sub = new UserSubscription();
                sub.setUser(userOpt.get());
                sub.setPlan(plan);
                sub.setMpPreapprovalId(preapprovalId);
                sub.setStatus(SubscriptionStatus.PENDING);
                sub = userSubscriptionRepository.save(sub);
                log.info("📋 Creando nueva suscripción a {} via pago para usuario: {}", plan.getName(), payerEmail);
            } else {
                sub = subOpt.get();
            }

            // Registrar el pago — reusa la fila existente si ya la había
            // (ej. la que quedó en "pending"), en vez de crear una nueva.
            SubscriptionPayment payment = pagoExistente.orElseGet(SubscriptionPayment::new);
            payment.setSubscription(sub);
            payment.setMpPaymentId(paymentId);
            payment.setStatus(status);
            if (paymentData.get("transactionAmount") != null) {
                payment.setAmount(new BigDecimal(paymentData.get("transactionAmount").toString()));
            }
            if ("approved".equals(status)) {
                payment.setPaidAt(LocalDateTime.now());
                // sub.status ahora se activa acá — es el único evento que
                // representa un cobro real confirmado. El webhook de
                // suscripción (processWebhookSubscription) ya no toca el
                // status ni las fechas, solo actualiza metadata.
                if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    sub.setStartDate(LocalDateTime.now());
                }
                sub.setEndDate(LocalDateTime.now().plusMonths(1));
                sub.setLastPaymentStatus("approved");
                sub.setLastPaymentDate(LocalDateTime.now());
                activatePlanOnUser(sub.getUser(), sub.getPlan());
                log.info("✅ Pago aprobado — suscripción {} renovada para: {}", sub.getPlan().getName(), sub.getUser().getEmail());
            } else if ("rejected".equals(status)) {
                sub.setLastPaymentStatus("rejected");
                sub.setLastPaymentDate(LocalDateTime.now());
                log.warn("⚠️ Pago rechazado para usuario: {}", sub.getUser().getEmail());
            }

            subscriptionPaymentRepository.save(payment);
            userSubscriptionRepository.save(sub);

        } catch (Exception e) {
            log.error("❌ Error procesando webhook pago {}: {}", paymentId, e.getMessage());
        }
    }

    // ==============================================
    // CONFIRMAR SUSCRIPCIÓN PENDIENTE
    // ==============================================

    @Transactional
    public void confirmarSuscripcionPendiente(String token, String userEmail) {
        SubscriptionPendingConfirmation pending = pendingConfirmationRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token inválido o no encontrado"));

        if (pending.isExpired()) {
            throw new RuntimeException("El link de confirmación expiró. Contactá a info@cinemarketer.com.ar");
        }

        if (pending.isConfirmed()) {
            throw new RuntimeException("Esta suscripción ya fue confirmada anteriormente");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Idempotencia real: el pago detrás de este token puede haber sido
        // procesado igual por el webhook normal mientras el mail de confirmación
        // seguía sin abrirse (ej. el email de MP no coincidía al momento del
        // webhook pero se resolvió solo en un reintento posterior). Si ese
        // mp_payment_id ya está aprobado en el sistema, este click es tardío
        // y redundante — solo lo marcamos como usado, sin crear una segunda
        // suscripción ni reactivar el plan.
        if (pending.getMpPaymentId() != null) {
            Optional<SubscriptionPayment> pagoYaProcesado =
                    subscriptionPaymentRepository.findByMpPaymentId(pending.getMpPaymentId());
            if (pagoYaProcesado.isPresent() && "approved".equals(pagoYaProcesado.get().getStatus())) {
                pending.setConfirmedAt(LocalDateTime.now());
                pending.setConfirmedUserId(user.getId());
                pendingConfirmationRepository.save(pending);
                log.info("↩️ Pago {} del token {} ya estaba aprobado por otra vía, confirmación tardía ignorada para: {}",
                        pending.getMpPaymentId(), token, userEmail);
                return;
            }
        }

        // Identificar el plan real por el dato guardado al crear la confirmación
        // pendiente — nunca asumir Premium por defecto, para no activarle a
        // alguien un plan distinto del que efectivamente pagó.
        SubscriptionPlan plan = (pending.getMpPreapprovalPlanId() != null)
                ? subscriptionPlanRepository.findByMpPreapprovalPlanId(pending.getMpPreapprovalPlanId())
                .orElseThrow(() -> new RuntimeException("No se pudo identificar el plan pagado"))
                : subscriptionPlanRepository.findByNameAndActiveTrue("Premium")
                .orElseThrow(() -> new RuntimeException("No hay plan Premium activo"));

        // Crear la suscripción
        UserSubscription sub = new UserSubscription();
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setMpPreapprovalId(pending.getMpPreapprovalId());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusMonths(1));
        sub.setNextBillingDate(LocalDateTime.now().plusMonths(1));
        sub.setLastPaymentStatus("approved");
        sub.setLastPaymentDate(LocalDateTime.now());
        userSubscriptionRepository.save(sub);

        // Activar premium
        activatePlanOnUser(user, plan);

        // Marcar confirmación como usada
        pending.setConfirmedAt(LocalDateTime.now());
        pending.setConfirmedUserId(user.getId());
        pendingConfirmationRepository.save(pending);

        log.info("✅ Suscripción confirmada para usuario: {} (MP email: {})", userEmail, pending.getMpPayerEmail());
    }

    // ==============================================
    // CANCELAR SUSCRIPCIÓN
    // ==============================================

    @Transactional
    public void cancelSubscription(UserSubscription sub) {
        if (sub.getMpPreapprovalId() != null) {
            try {
                mercadoPagoService.cancelSubscription(sub.getMpPreapprovalId());
            } catch (Exception e) {
                log.warn("⚠️ No se pudo cancelar en MP, continuando cancelación local: {}", e.getMessage());
            }
        }

        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setCancelledAt(LocalDateTime.now());
        userSubscriptionRepository.save(sub);

        if (sub.getEndDate() != null && sub.getEndDate().isBefore(LocalDateTime.now())) {
            deactivatePlanOnUser(sub.getUser(), sub.getPlan());
        }

        log.info("🚫 Suscripción cancelada para usuario: {}", sub.getUser().getEmail());
    }

    // ==============================================
    // ALTA MANUAL (ADMIN)
    // ==============================================

    @Transactional
    public UserSubscription activateManually(Long userId, Map<String, Object> body) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        SubscriptionPlan plan = subscriptionPlanRepository.findByNameAndActiveTrue("Premium")
                .orElseThrow(() -> new RuntimeException("No hay plan Premium activo"));

        UserSubscription sub = new UserSubscription();
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusMonths(1));
        sub.setNextBillingDate(LocalDateTime.now().plusMonths(1));
        sub.setLastPaymentStatus("approved");
        sub.setLastPaymentDate(LocalDateTime.now());

        userSubscriptionRepository.save(sub);

        // Registrar pago manual en historial
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setSubscription(sub);
        payment.setMpPaymentId("MANUAL-" + userId + "-" + System.currentTimeMillis());
        payment.setStatus("approved");
        payment.setAmount(new BigDecimal("999.00"));
        payment.setPaidAt(LocalDateTime.now());
        subscriptionPaymentRepository.save(payment);

        activatePlanOnUser(user, plan);

        log.info("✅ Suscripción activada manualmente para usuario: {}", user.getEmail());
        return sub;
    }

    // ==============================================
    // CONVERSIÓN A DTO
    // ==============================================

    public SubscriptionDto toDto(UserSubscription sub) {
        SubscriptionPlan plan = sub.getPlan();
        User user = sub.getUser();
        SubscriptionDto dto = new SubscriptionDto();
        dto.setUserId(user.getId());
        dto.setUserEmail(user.getEmail());
        dto.setUserName(user.getName());
        dto.setPlanId(plan.getId());
        dto.setPlanName(plan.getName());
        dto.setPlanType(plan.getType());
        dto.setPlanPrice(plan.getPrice());
        dto.setPointsMultiplier(plan.getPointsMultiplier());
        dto.setBenefits(parseBenefits(plan.getBenefits()));
        dto.setSubscriptionId(sub.getId());
        dto.setStatus(sub.getStatus());
        dto.setStartDate(sub.getStartDate());
        dto.setEndDate(sub.getEndDate());
        dto.setNextBillingDate(sub.getNextBillingDate());
        dto.setLastPaymentStatus(sub.getLastPaymentStatus());
        dto.setLastPaymentDate(sub.getLastPaymentDate());
        dto.setActive(sub.isActive());
        dto.setMpPreapprovalId(sub.getMpPreapprovalId());
        return dto;
    }

    public Map<String, Object> toDtoWithPayments(UserSubscription sub) {
        Map<String, Object> result = new HashMap<>();
        result.put("subscription", toDto(sub));
        result.put("payments", subscriptionPaymentRepository
                .findBySubscriptionIdOrderByCreatedAtDesc(sub.getId())
                .stream()
                .map(p -> Map.of(
                        "id",          p.getId(),
                        "mpPaymentId", p.getMpPaymentId() != null ? p.getMpPaymentId() : "",
                        "amount",      p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO,
                        "status",      p.getStatus() != null ? p.getStatus() : "",
                        "paidAt",      p.getPaidAt() != null ? p.getPaidAt().toString() : "",
                        "createdAt",   p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""
                ))
                .toList());
        return result;
    }

    // ==============================================
    // HELPERS PRIVADOS
    // ==============================================

    // Activa el flag correcto según el plan de la suscripción — Premium o
    // Creator son independientes, uno no pisa al otro.
    //
    // OJO: antes esto siempre pisaba con "ahora + 1 mes", sin importar cuánto
    // le quedaba al usuario. Como este método lo llaman DOS webhooks distintos
    // para el mismo ciclo (processWebhookSubscription y processWebhookPayment),
    // el que llegaba último terminaba reseteando la fecha — por eso el
    // vencimiento real dependía de cuál aviso tardaba más, no de los pagos
    // reales. Ahora extiende desde el vencimiento actual si todavía es
    // futuro, y solo usa "ahora" como base si ya venció.
    private void activatePlanOnUser(User user, SubscriptionPlan plan) {
        LocalDateTime ahora = LocalDateTime.now();
        if ("Creator".equalsIgnoreCase(plan.getName())) {
            LocalDateTime base = (user.getCreatorUntil() != null && user.getCreatorUntil().isAfter(ahora))
                    ? user.getCreatorUntil() : ahora;
            user.setCreator(true);
            user.setCreatorUntil(base.plusMonths(1));
        } else {
            LocalDateTime base = (user.getPremiumUntil() != null && user.getPremiumUntil().isAfter(ahora))
                    ? user.getPremiumUntil() : ahora;
            user.setPremium(true);
            user.setPremiumUntil(base.plusMonths(1));
        }
        userRepository.save(user);
    }

    private void deactivatePlanOnUser(User user, SubscriptionPlan plan) {
        if ("Creator".equalsIgnoreCase(plan.getName())) {
            user.setCreator(false);
        } else {
            user.setPremium(false);
        }
        userRepository.save(user);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseBenefits(String benefitsJson) {
        if (benefitsJson == null || benefitsJson.isBlank()) return List.of();
        try {
            String cleaned = benefitsJson.replace("[", "").replace("]", "").replace("\"", "");
            return Arrays.asList(cleaned.split(","));
        } catch (Exception e) {
            return List.of();
        }
    }
}