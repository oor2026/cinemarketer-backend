package com.example.demo.web.controllers;

import com.example.demo.application.services.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/mercadopago")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final SubscriptionService subscriptionService;

    public MercadoPagoWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * POST /api/webhooks/mercadopago
     * Endpoint público — recibe notificaciones de Mercado Pago
     * Debe estar excluido de Spring Security (sin autenticación)
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        log.info("📩 Webhook MP recibido - type: {}, id: {}",
                payload.get("type"), payload.get("id"));

        try {
            String type = (String) payload.get("type");

            if ("subscription_preapproval".equals(type)) {
                // Notificación de cambio en suscripción
                Object dataObj = payload.get("data");
                if (dataObj instanceof Map<?, ?> data) {
                    String preapprovalId = String.valueOf(data.get("id"));
                    subscriptionService.processWebhookSubscription(preapprovalId);
                }

            } else if ("payment".equals(type)) {
                // Notificación de pago de cuota
                Object dataObj = payload.get("data");
                if (dataObj instanceof Map<?, ?> data) {
                    String paymentId = String.valueOf(data.get("id"));
                    subscriptionService.processWebhookPayment(paymentId);
                }

            } else {
                log.debug("Tipo de webhook no manejado: {}", type);
            }

        } catch (Exception e) {
            // Siempre devolver 200 a MP para que no reintente
            log.error("❌ Error procesando webhook MP: {}", e.getMessage(), e);
        }

        // MP requiere 200 OK siempre, incluso si hubo error interno
        return ResponseEntity.ok().build();
    }
}
