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

@Service
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);
    private static final String MP_BASE_URL = "https://api.mercadopago.com";

    private final RestClient restClient;

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @Value("${mercadopago.public-key}")
    private String publicKey;

    @Value("${mercadopago.sandbox:false}")
    private boolean sandbox;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${mercadopago.plan-url:https://mpago.la/1jbrd2K}")
    private String planUrl;

    public MercadoPagoService() {
        this.restClient = RestClient.builder()
                .baseUrl(MP_BASE_URL)
                .build();
    }

    /**
     * Devuelve la URL del plan de suscripción.
     * El usuario paga con cualquier cuenta de MP sin restricción de email.
     * El webhook llega con el preapproval_id y payer_email del pagador.
     */
    public Map<String, Object> createSubscription(User user) {
        log.info("🔄 Iniciando suscripción para usuario: {}", user.getEmail());

        Map<String, Object> result = new HashMap<>();
        result.put("preapprovalId", null);
        result.put("initPoint", planUrl);
        result.put("publicKey", publicKey);
        result.put("sandbox", sandbox);
        return result;
    }

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
                result.put("payerEmail", response.get("payer_email"));
            }
            return result;

        } catch (Exception e) {
            log.error("❌ Error consultando suscripción MP {}: {}", preapprovalId, e.getMessage());
            throw new RuntimeException("Error al consultar suscripción en Mercado Pago");
        }
    }

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

    public String getPublicKey() { return publicKey; }
    public boolean isSandbox() { return sandbox; }
}