package com.example.demo.web.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Genera el preview de Open Graph para compartir el resultado del Tótem
 * público (test anónimo, sin cuenta). No hay ningún registro en base —
 * el nombre y emoji del tótem llegan ya calculados desde el frontend
 * (totem-publico.html) por query param. El destino final siempre es la
 * misma página del test, nunca un resultado guardado.
 *
 * GET /api/totem/og?nombre=Bang&emoji=%F0%9F%92%A5
 */
@RestController
@RequestMapping("/api/totem")
@CrossOrigin(origins = "*")
public class TotemShareController {

    @GetMapping("/og")
    public ResponseEntity<String> ogRedirect(
            @RequestParam(defaultValue = "Cinéfilo") String nombre,
            @RequestParam(defaultValue = "🎬") String emoji) {

        // Sin validar contra ninguna lista — es solo texto decorativo para
        // el preview, no hay lookup ni persistencia de por medio. Se recorta
        // por las dudas para que nadie arme un preview absurdamente largo.
        String nombreSeguro = nombre.length() > 40 ? nombre.substring(0, 40) : nombre;
        String emojiSeguro  = emoji.length() > 8 ? emoji.substring(0, 8) : emoji;

        // Texto mínimo a propósito: el preview tiene que vivir de la imagen,
        // no de una oración larga. nombreSeguro/emojiSeguro quedan tomados
        // igual (por si más adelante arman una imagen dinámica por tótem
        // acá mismo), pero ya no se usan en el título.
        String titulo = "Descubrí tu espíritu cinéfilo 🎬";
        String imagen = "https://cinemarketer.com.ar/assets/images/og-default.png";

        String html = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta property="og:title" content="%s">
                <meta property="og:image" content="%s">
                <meta property="og:image:width" content="1200">
                <meta property="og:image:height" content="630">
                <meta property="og:url" content="https://cinemarketer.com.ar/totem-publico">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="Cinemarketer">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="%s">
                <meta name="twitter:image" content="%s">
                <meta http-equiv="refresh" content="0;url=https://cinemarketer.com.ar/totem-publico">
                <script>window.location.href='https://cinemarketer.com.ar/totem-publico';</script>
            </head>
            <body></body>
            </html>
            """.formatted(titulo, imagen, titulo, imagen);

        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(html);
    }
}