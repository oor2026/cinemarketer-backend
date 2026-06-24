package com.example.demo.application.services;

import com.example.demo.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente REST para la API de Mercado Pago.
 * Usa RestClient (Spring 6+) sin SDK externo para evitar conflictos con Spring Boot 4.
 */
@Service
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);
    private static final String MP_BASE_URL = "https://api.mercadopago.com";

    private final RestClient restClient;

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Value("${mercadopago.public-key}")
    private String publicKey;

    @Value("${mercadopago.sandbox:true}")
    private boolean sandbox;

    // URL base del backend — necesaria para el webhook
    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${mercadopago.payer-email:info@cinemarketer.com.ar}")
    private String payerEmail;

    public MercadoPagoService() {
        this.restClient = RestClient.builder()
                .baseUrl(MP_BASE_URL)
                .build();
    }

    // ==============================================
    // CREAR SUSCRIPCIÓN EN MERCADO PAGO
    // ==============================================

    /**
     * Crea una suscripción (preapproval) en MP para el usuario.
     * Devuelve el init_point y el preapproval_id para el frontend.
     */
    public Map<String, Object> createSubscription(User user) {
        log.info("🔄 Creando suscripción en MP para usuario: {}", user.getEmail());

        Map<String, Object> body = new HashMap<>();
        body.put("reason", "Cinemarketer Premium - Suscripción Mensual");
        body.put("auto_recurring", Map.of(
                "frequency", 1,
                "frequency_type", "months",
                "transaction_amount", getPlanPrice(),
                "currency_id", "ARS"
        ));
        body.put("back_url", frontendUrl + "/dashboard.html?module=mi-cuenta");
        body.put("payer_email", payerEmail);
        body.put("status", "pending");
        body.put("notification_url", appBaseUrl + "/api/webhooks/mercadopago");

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/preapproval")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            log.info("✅ Suscripción MP creada: {}", response != null ? response.get("id") : "null");

            Map<String, Object> result = new HashMap<>();
            result.put("preapprovalId", response != null ? response.get("id") : null);
            result.put("initPoint", response != null ? response.get("init_point") : null);
            result.put("publicKey", publicKey);
            result.put("sandbox", sandbox);
            return result;

        } catch (Exception e) {
            log.error("❌ Error creando suscripción en MP: {}", e.getMessage(), e);
            throw new RuntimeException("Error al conectar con Mercado Pago: " + e.getMessage());
        }
    }

    // ==============================================
    // CONSULTAR SUSCRIPCIÓN EN MERCADO PAGO
    // ==============================================

    /**
     * Consulta el estado de una suscripción por su preapproval_id
     */
    public Map<String, Object> getSubscription(String preapprovalId) {
        log.info("🔍 Consultando suscripción MP: {}", preapprovalId);

        try {
            Map<?, ?> response = restClient.get()
                    .uri("/preapproval/" + preapprovalId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> result = new HashMap<>();
            if (response != null) {
                result.put("id", response.get("id"));
                result.put("status", response.get("status"));
                result.put("nextPaymentDate", response.get("next_payment_date"));
                result.put("lastModified", response.get("last_modified"));
            }
            return result;

        } catch (Exception e) {
            log.error("❌ Error consultando suscripción MP {}: {}", preapprovalId, e.getMessage());
            throw new RuntimeException("Error al consultar suscripción en Mercado Pago");
        }
    }

    // ==============================================
    // CANCELAR SUSCRIPCIÓN EN MERCADO PAGO
    // ==============================================

    /**
     * Cancela una suscripción activa en MP
     */
    public void cancelSubscription(String preapprovalId) {
        log.info("🚫 Cancelando suscripción MP: {}", preapprovalId);

        try {
            restClient.put()
                    .uri("/preapproval/" + preapprovalId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("status", "cancelled"))
                    .retrieve()
                    .toBodilessEntity();

            log.info("✅ Suscripción MP cancelada: {}", preapprovalId);

        } catch (Exception e) {
            log.error("❌ Error cancelando suscripción MP {}: {}", preapprovalId, e.getMessage());
            throw new RuntimeException("Error al cancelar suscripción en Mercado Pago");
        }
    }

    // ==============================================
    // CONSULTAR PAGO EN MERCADO PAGO
    // ==============================================

    /**
     * Consulta un pago por su payment_id
     */
    public Map<String, Object> getPayment(String paymentId) {
        log.info("🔍 Consultando pago MP: {}", paymentId);

        try {
            Map<?, ?> response = restClient.get()
                    .uri("/v1/payments/" + paymentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> result = new HashMap<>();
            if (response != null) {
                result.put("id", response.get("id"));
                result.put("status", response.get("status"));
                result.put("transactionAmount", response.get("transaction_amount"));
                result.put("dateApproved", response.get("date_approved"));
                result.put("preapprovalId", response.get("preapproval_id"));
            }
            return result;

        } catch (Exception e) {
            log.error("❌ Error consultando pago MP {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Error al consultar pago en Mercado Pago");
        }
    }

    // ==============================================
    // HELPERS
    // ==============================================

    /**
     * Precio del plan — en producción vendría de la BD.
     */
    @Value("${subscription.price:999.0}")
    private double subscriptionPrice;

    private double getPlanPrice() {
        return subscriptionPrice;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public boolean isSandbox() {
        return sandbox;
    }
}
