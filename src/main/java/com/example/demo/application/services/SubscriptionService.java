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
    private final UserRepository userRepository;
    private final MercadoPagoService mercadoPagoService;

    public SubscriptionService(UserSubscriptionRepository userSubscriptionRepository,
                               SubscriptionPlanRepository subscriptionPlanRepository,
                               SubscriptionPaymentRepository subscriptionPaymentRepository,
                               UserRepository userRepository,
                               MercadoPagoService mercadoPagoService) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.userRepository = userRepository;
        this.mercadoPagoService = mercadoPagoService;
    }

    // ==============================================
    // VERIFICAR SI EL USUARIO ES PREMIUM ACTIVO
    // ==============================================

    public boolean isActivePremium(Long userId) {
        Optional<UserSubscription> sub = userSubscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        return sub.isPresent() && sub.get().isActive();
    }

    // ==============================================
    // OBTENER INFO DEL PLAN
    // ==============================================

    public SubscriptionDto getPlanInfo() {
        SubscriptionPlan plan = subscriptionPlanRepository.findFirstByActiveTrue()
                .orElseThrow(() -> new RuntimeException("No hay planes de suscripción activos"));

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

            Optional<UserSubscription> subOpt = userSubscriptionRepository
                    .findByMpPreapprovalId(preapprovalId);

            UserSubscription sub;

            if (subOpt.isEmpty()) {
                // Suscripción via plan de MP — no hay registro previo en DB
                // Usamos el payer_email para identificar al usuario
                if (payerEmail == null || payerEmail.isBlank()) {
                    log.warn("⚠️ No se encontró payer_email para preapprovalId: {}", preapprovalId);
                    return;
                }

                Optional<User> userOpt = userRepository.findByEmail(payerEmail);
                if (userOpt.isEmpty()) {
                    log.warn("⚠️ No se encontró usuario con email: {}", payerEmail);
                    return;
                }

                SubscriptionPlan plan = subscriptionPlanRepository.findFirstByActiveTrue()
                        .orElse(null);
                if (plan == null) {
                    log.warn("⚠️ No hay plan activo para crear suscripción");
                    return;
                }

                sub = new UserSubscription();
                sub.setUser(userOpt.get());
                sub.setPlan(plan);
                sub.setMpPreapprovalId(preapprovalId);
                sub.setStatus(SubscriptionStatus.PENDING);
                log.info("📋 Creando nueva suscripción via plan para usuario: {}", payerEmail);

            } else {
                sub = subOpt.get();
            }

            switch (mpStatus) {
                case "authorized" -> {
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    sub.setStartDate(LocalDateTime.now());
                    sub.setEndDate(LocalDateTime.now().plusMonths(1));
                    String nextBilling = (String) mpData.get("nextPaymentDate");
                    if (nextBilling != null) {
                        sub.setNextBillingDate(LocalDateTime.parse(nextBilling.substring(0, 19)));
                    }
                    activatePremiumOnUser(sub.getUser());
                    log.info("✅ Suscripción activada para usuario: {}", sub.getUser().getEmail());
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
                // Pago de suscripción via plan — no hay registro previo en DB
                // Usamos el payer_email para identificar al usuario
                String payerEmail = (String) paymentData.get("payerEmail");
                if (payerEmail == null || payerEmail.isBlank()) {
                    log.warn("⚠️ Pago {} sin preapprovalId en DB ni payerEmail", paymentId);
                    return;
                }

                Optional<User> userOpt = userRepository.findByEmail(payerEmail);
                if (userOpt.isEmpty()) {
                    log.warn("⚠️ No se encontró usuario con email: {}", payerEmail);
                    return;
                }

                SubscriptionPlan plan = subscriptionPlanRepository.findFirstByActiveTrue().orElse(null);
                if (plan == null) return;

                sub = new UserSubscription();
                sub.setUser(userOpt.get());
                sub.setPlan(plan);
                sub.setMpPreapprovalId(preapprovalId);
                sub.setStatus(SubscriptionStatus.PENDING);
                sub = userSubscriptionRepository.save(sub);
                log.info("📋 Creando nueva suscripción via pago para usuario: {}", payerEmail);
            } else {
                sub = subOpt.get();
            }

            // Registrar el pago
            SubscriptionPayment payment = new SubscriptionPayment();
            payment.setSubscription(sub);
            payment.setMpPaymentId(paymentId);
            payment.setStatus(status);
            if (paymentData.get("transactionAmount") != null) {
                payment.setAmount(new BigDecimal(paymentData.get("transactionAmount").toString()));
            }
            if ("approved".equals(status)) {
                payment.setPaidAt(LocalDateTime.now());
                sub.setEndDate(LocalDateTime.now().plusMonths(1));
                sub.setLastPaymentStatus("approved");
                sub.setLastPaymentDate(LocalDateTime.now());
                activatePremiumOnUser(sub.getUser());
                log.info("✅ Pago aprobado — suscripción renovada para: {}", sub.getUser().getEmail());
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
            deactivatePremiumOnUser(sub.getUser());
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

        SubscriptionPlan plan = subscriptionPlanRepository.findFirstByActiveTrue()
                .orElseThrow(() -> new RuntimeException("No hay planes activos"));

        UserSubscription sub = new UserSubscription();
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusMonths(1));
        sub.setNextBillingDate(LocalDateTime.now().plusMonths(1));
        sub.setLastPaymentStatus("manual");
        sub.setLastPaymentDate(LocalDateTime.now());

        userSubscriptionRepository.save(sub);
        activatePremiumOnUser(user);

        log.info("✅ Suscripción activada manualmente para usuario: {}", user.getEmail());
        return sub;
    }

    // ==============================================
    // CONVERSIÓN A DTO
    // ==============================================

    public SubscriptionDto toDto(UserSubscription sub) {
        SubscriptionPlan plan = sub.getPlan();
        SubscriptionDto dto = new SubscriptionDto();
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
                .findBySubscriptionIdOrderByCreatedAtDesc(sub.getId()));
        return result;
    }

    // ==============================================
    // HELPERS PRIVADOS
    // ==============================================

    private void activatePremiumOnUser(User user) {
        user.setPremium(true);
        user.setPremiumUntil(LocalDateTime.now().plusMonths(1));
        userRepository.save(user);
    }

    private void deactivatePremiumOnUser(User user) {
        user.setPremium(false);
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