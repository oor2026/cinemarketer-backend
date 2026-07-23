package com.example.demo.application.services;

import com.example.demo.domain.moderation.ImageModeration;
import com.example.demo.domain.moderation.ImageModerationRepository;
import com.example.demo.domain.moderation.NivelRiesgoImagen;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class ImageModerationService {

    private static final double UMBRAL_ALTO = 0.85;
    private static final double UMBRAL_GRIS = 0.40;

    private final ImageModerationRepository imageModerationRepository;
    private final RestTemplate restTemplate;

    @Value("${nsfw.service.url}")
    private String nsfwServiceUrl;

    @Value("${nsfw.service.token}")
    private String nsfwServiceToken;

    public ImageModerationService(ImageModerationRepository imageModerationRepository,
                                  RestTemplate restTemplate) {
        this.imageModerationRepository = imageModerationRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * Clasifica una imagen recién subida contra el microservicio NSFWJS y
     * guarda el resultado. Fail-closed: si el servicio no responde o falla,
     * tira excepción — quien llame a esto debe rechazar la subida.
     */
    @SuppressWarnings("unchecked")
    public ImageModeration clasificarYGuardar(String imageUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(nsfwServiceToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("imageUrl", imageUrl), headers);

        Map<String, Object> body;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    nsfwServiceUrl + "/clasificar", request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Respuesta inválida del servicio de moderación.");
            }
            body = response.getBody();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No pudimos verificar la imagen en este momento. Intentá de nuevo en unos segundos.");
        }

        Map<String, Object> scores = (Map<String, Object>) body.get("scores");
        if (scores == null) {
            throw new IllegalStateException(
                    "No pudimos verificar la imagen en este momento. Intentá de nuevo en unos segundos.");
        }

        double neutral = toDouble(scores.get("neutral"));
        double drawing = toDouble(scores.get("drawing"));
        double sexy = toDouble(scores.get("sexy"));
        double porn = toDouble(scores.get("porn"));
        double hentai = toDouble(scores.get("hentai"));

        double scoreRiesgo = Math.max(porn, hentai);
        NivelRiesgoImagen nivel;
        if (scoreRiesgo >= UMBRAL_ALTO) {
            nivel = NivelRiesgoImagen.ALTO;
        } else if (scoreRiesgo >= UMBRAL_GRIS) {
            nivel = NivelRiesgoImagen.GRIS;
        } else {
            nivel = NivelRiesgoImagen.BAJO;
        }

        ImageModeration registro = new ImageModeration();
        registro.setImageUrl(imageUrl);
        registro.setScoreNeutral(neutral);
        registro.setScoreDrawing(drawing);
        registro.setScoreSexy(sexy);
        registro.setScorePorn(porn);
        registro.setScoreHentai(hentai);
        registro.setNivelRiesgo(nivel);
        return imageModerationRepository.save(registro);
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0.0;
    }
}