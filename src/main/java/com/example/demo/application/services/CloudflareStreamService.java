package com.example.demo.application.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudflareStreamService {

    private final RestTemplate restTemplate;

    @Value("${cloudflare.stream.account-id}")
    private String accountId;

    @Value("${cloudflare.stream.api-token}")
    private String apiToken;

    public CloudflareStreamService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Sube el video a Cloudflare Stream. Fail-closed: si falla, tira excepción
     * — quien llame a esto debe rechazar la subida (mismo criterio que ya
     * usamos en ImageModerationService para imagen).
     * Devuelve el UID del video en Cloudflare (no la URL de reproducción final).
     */
    @SuppressWarnings("unchecked")
    public String subirVideo(MultipartFile file) {
        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/stream";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", fileResource);
        } catch (IOException e) {
            throw new IllegalStateException("No pudimos leer el archivo de video. Intentá de nuevo.");
        }

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        Map<String, Object> responseBody;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Respuesta inválida de Cloudflare Stream.");
            }
            responseBody = response.getBody();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No pudimos subir el video en este momento. Intentá de nuevo en unos segundos.");
        }

        Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
        if (result == null || result.get("uid") == null) {
            throw new IllegalStateException(
                    "No pudimos subir el video en este momento. Intentá de nuevo en unos segundos.");
        }
        return (String) result.get("uid");
    }

    /**
     * Consulta si el video ya terminó de encodear en Cloudflare.
     * No es fail-closed — si falla, devuelve "no listo todavía", así el
     * scheduler simplemente reintenta en la próxima pasada sin romper nada.
     */
    @SuppressWarnings("unchecked")
    public VideoEstado consultarEstado(String videoUid) {
        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/stream/" + videoUid;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return VideoEstado.noListo();

            Map<String, Object> result = (Map<String, Object>) body.get("result");
            if (result == null) return VideoEstado.noListo();

            boolean listo = Boolean.TRUE.equals(result.get("readyToStream"));
            double duracion = result.get("duration") instanceof Number
                    ? ((Number) result.get("duration")).doubleValue() : -1;

            return new VideoEstado(listo, duracion);
        } catch (Exception e) {
            return VideoEstado.noListo();
        }
    }

    /**
     * Arma las URLs de 5 frames distribuidos a lo largo del video (0%, 25%,
     * 50%, 75%, 100% de su duración), vía transformación de URL de Cloudflare
     * — sin llamada a API adicional, igual que la extracción de thumbnails
     * de Cloudinary.
     */
    public String[] obtenerFramesUrls(String videoUid, double duracionSegundos) {
        double dur = Math.max(duracionSegundos, 1.0);
        double[] porcentajes = {0.0, 0.25, 0.5, 0.75, 0.98}; // 0.98 en vez de 1.0 para evitar el borde exacto final
        String[] urls = new String[porcentajes.length];
        for (int i = 0; i < porcentajes.length; i++) {
            double segundos = dur * porcentajes[i];
            urls[i] = "https://videodelivery.net/" + videoUid + "/thumbnails/thumbnail.jpg?time="
                    + String.format(java.util.Locale.US, "%.1f", segundos) + "s";
        }
        return urls;
    }

    /** URL de reproducción final (HLS), para guardar en la publicación una vez aprobada. */
    public String obtenerUrlReproduccion(String videoUid) {
        return "https://videodelivery.net/" + videoUid + "/manifest/video.m3u8";
    }

    public record VideoEstado(boolean listo, double duracionSegundos) {
        public static VideoEstado noListo() {
            return new VideoEstado(false, -1);
        }
    }
}