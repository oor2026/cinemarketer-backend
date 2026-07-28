package com.example.demo.web.controllers;

import com.example.demo.application.services.PremiumRewardService;
import com.example.demo.domain.premium.PremiumReward;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/premium/rewards")
@CrossOrigin(origins = "*")
public class PremiumRewardShareController {

    private final PremiumRewardService premiumRewardService;

    public PremiumRewardShareController(PremiumRewardService premiumRewardService) {
        this.premiumRewardService = premiumRewardService;
    }

    @GetMapping("/og/{id}")
    public ResponseEntity<String> ogRedirect(@PathVariable Long id) {
        try {
            PremiumReward reward = premiumRewardService.getRewardEntityById(id)
                    .orElseThrow(() -> new RuntimeException("Premio no encontrado"));

            String titulo = reward.getName() != null ? reward.getName() : "Premio exclusivo";
            String imagen = reward.getImageUrl() != null
                    ? reward.getImageUrl()
                    : "https://cinemarketer.com.ar/assets/images/isologotipo.webp";

            String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <meta property="og:title" content="%s — Cinemarketer">
                    <meta property="og:image" content="%s">
                    <meta property="og:image:type" content="image/jpeg">
                    <meta property="og:description" content="Premio exclusivo Premium en Cinemarketer ⭐">
                    <meta property="og:url" content="https://cinemarketer.com.ar/premio-publico?id=%d&tipo=especial">
                    <meta property="og:type" content="website">
                    <meta property="og:site_name" content="Cinemarketer">
                    <meta name="twitter:card" content="summary_large_image">
                    <meta name="twitter:title" content="%s — Cinemarketer">
                    <meta name="twitter:image" content="%s">
                    <meta name="twitter:description" content="Premio exclusivo Premium en Cinemarketer ⭐">
                    <meta http-equiv="refresh" content="0;url=https://cinemarketer.com.ar/premio-publico?id=%d&tipo=especial">
                    <script>window.location.href='https://cinemarketer.com.ar/premio-publico?id=%d&tipo=especial';</script>
                </head>
                <body></body>
                </html>
                """.formatted(titulo, imagen, id, titulo, imagen, id, id);

            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(html);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}