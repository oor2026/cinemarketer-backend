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
        String imagen = "https://cinemarketer-backend-production.up.railway.app/api/totem/og-image?nombre="
                + java.net.URLEncoder.encode(nombreSeguro, java.nio.charset.StandardCharsets.UTF_8)
                + "&emoji=" + java.net.URLEncoder.encode(emojiSeguro, java.nio.charset.StandardCharsets.UTF_8);

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

    /**
     * Genera al vuelo la imagen del preview (1200x630) para el og:image —
     * nada estático, todo se dibuja acá con los mismos datos que ya llegan
     * por query param. Cacheable por un día ya que el resultado es siempre
     * el mismo para un mismo nombre+emoji.
     * GET /api/totem/og-image?nombre=Bang&emoji=%F0%9F%92%A5
     */
    @GetMapping(value = "/og-image", produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> ogImage(
            @RequestParam(defaultValue = "Cinéfilo") String nombre,
            @RequestParam(defaultValue = "🎬") String emoji) throws java.io.IOException {

        String nombreSeguro = nombre.length() > 30 ? nombre.substring(0, 30) : nombre;

        int width = 1200, height = 630;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fondo — degradé diagonal azul de marca hacia el negro-azulado del sitio
        java.awt.GradientPaint fondo = new java.awt.GradientPaint(
                0, 0, new java.awt.Color(0x32, 0x4C, 0x89),
                width, height, new java.awt.Color(0x0D, 0x0E, 0x1A));
        g.setPaint(fondo);
        g.fillRect(0, 0, width, height);

        // Marca de agua CINEMARKETER, sutil y rotada
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 90));
        g.setColor(new java.awt.Color(255, 255, 255, 18));
        g.rotate(Math.toRadians(-8), width / 2.0, height / 2.0);
        g.drawString("CINEMARKETER", -60, height - 40);
        g.rotate(Math.toRadians(8), width / 2.0, height / 2.0);

        // Emoji del tótem, grande y centrado
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 160));
        g.setColor(java.awt.Color.WHITE);
        java.awt.FontMetrics fmEmoji = g.getFontMetrics();
        int emojiWidth = fmEmoji.stringWidth(emoji);
        g.drawString(emoji, (width - emojiWidth) / 2, 260);

        // "Sos {nombre}"
        String titulo = "Sos " + nombreSeguro;
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 72));
        java.awt.FontMetrics fmTitulo = g.getFontMetrics();
        int tituloWidth = fmTitulo.stringWidth(titulo);
        g.setColor(java.awt.Color.WHITE);
        g.drawString(titulo, (width - tituloWidth) / 2, 400);

        // CTA en píldora roja
        String cta = "Descubrí tu espíritu cinéfilo";
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 38));
        java.awt.FontMetrics fmCta = g.getFontMetrics();
        int ctaWidth = fmCta.stringWidth(cta);
        int pillPaddingX = 50, pillHeight = 80;
        int pillWidth = ctaWidth + pillPaddingX * 2;
        int pillX = (width - pillWidth) / 2;
        int pillY = 480;
        g.setColor(new java.awt.Color(0xE5, 0x09, 0x14));
        g.fillRoundRect(pillX, pillY, pillWidth, pillHeight, pillHeight, pillHeight);
        g.setColor(java.awt.Color.WHITE);
        g.drawString(cta, pillX + pillPaddingX, pillY + pillHeight / 2 + fmCta.getAscent() / 2 - 6);

        g.dispose();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);

        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=86400")
                .body(baos.toByteArray());
    }
}