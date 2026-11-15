package com.example.demo.domain.mision;

/**
 * Revisa la imagen que el admin sube para un sello ANTES de mandarla a Cloudinary. No confía en el nombre ni en el tipo que declara el navegador: mira los
 * primeros bytes (así no entra un SVG, un ejecutable o cualquier cosa disfrazada) y lee el tamaño de la propia imagen. Java puro, sin librerías.
 * Reglas: PNG, WebP o JPG · hasta 500 KB · cuadrada (el sello es un círculo) · entre 256 y 2048 px de lado.
 */
public final class ValidadorImagenSello {
    private ValidadorImagenSello() {}

    public static final int MAX_BYTES = 500 * 1024, MIN_LADO = 256, MAX_LADO = 2048;

    public record Resultado(boolean ok, String error, String tipo, int ancho, int alto) {
        static Resultado mal(String e) { return new Resultado(false, e, null, 0, 0); }
    }

    public static Resultado validar(byte[] d, String tipoDeclarado) {
        if (d == null || d.length == 0) return Resultado.mal("No se recibió ninguna imagen.");
        if (d.length > MAX_BYTES) return Resultado.mal("La imagen pesa " + (d.length / 1024) + " KB y el máximo es " + (MAX_BYTES / 1024) + " KB. Comprimila y probá de nuevo.");
        String tipo = tipoReal(d);
        if (tipo == null) return Resultado.mal("Formato no permitido. Usá PNG, WebP o JPG.");
        if (tipoDeclarado != null && !tipoDeclarado.isBlank() && !tipoDeclarado.equalsIgnoreCase("application/octet-stream")) {
            String declarado = tipoDeclarado.toLowerCase().replace("image/jpg", "image/jpeg");
            if (!declarado.equals("image/" + tipo)) return Resultado.mal("El archivo no es del tipo que dice ser (dice " + tipoDeclarado + " pero es " + tipo.toUpperCase() + ").");
        }
        int[] medida = switch (tipo) { case "png" -> medidaPng(d); case "jpeg" -> medidaJpeg(d); default -> medidaWebp(d); };
        if (medida == null) return Resultado.mal("No se pudo leer el tamaño de la imagen: el archivo parece dañado.");
        int w = medida[0], h = medida[1];
        if (w < MIN_LADO || h < MIN_LADO) return Resultado.mal("La imagen es demasiado chica (" + w + "×" + h + "). Mínimo " + MIN_LADO + "×" + MIN_LADO + " píxeles.");
        if (w > MAX_LADO || h > MAX_LADO) return Resultado.mal("La imagen es demasiado grande (" + w + "×" + h + "). Máximo " + MAX_LADO + "×" + MAX_LADO + " píxeles.");
        if (Math.abs(w - h) > Math.max(w, h) * 0.05) return Resultado.mal("La imagen tiene que ser cuadrada: la tuya es de " + w + "×" + h + ". El sello es un círculo y una imagen alargada se recortaría.");
        return new Resultado(true, null, tipo, w, h);
    }

    /** png, jpeg o webp según los primeros bytes; null si no es ninguno. */
    static String tipoReal(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G' && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) return "png";
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "jpeg";
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "webp";
        return null;
    }

    private static int u8(byte[] d, int i) { return d[i] & 0xFF; }

    private static int[] medidaPng(byte[] d) {
        if (d.length < 24) return null;
        int w = (u8(d, 16) << 24) | (u8(d, 17) << 16) | (u8(d, 18) << 8) | u8(d, 19);
        int h = (u8(d, 20) << 24) | (u8(d, 21) << 16) | (u8(d, 22) << 8) | u8(d, 23);
        return w > 0 && h > 0 ? new int[]{w, h} : null;
    }

    /** Recorre los bloques del JPG hasta el que trae el tamaño (SOF). */
    private static int[] medidaJpeg(byte[] d) {
        int i = 2;
        while (i + 9 < d.length) {
            if (u8(d, i) != 0xFF) return null;
            int marca = u8(d, i + 1);
            if (marca == 0xFF) { i++; continue; }
            boolean sof = marca >= 0xC0 && marca <= 0xCF && marca != 0xC4 && marca != 0xC8 && marca != 0xCC;
            if (sof) {
                int h = (u8(d, i + 5) << 8) | u8(d, i + 6), w = (u8(d, i + 7) << 8) | u8(d, i + 8);
                return w > 0 && h > 0 ? new int[]{w, h} : null;
            }
            i += 2 + ((u8(d, i + 2) << 8) | u8(d, i + 3));
        }
        return null;
    }

    /** WebP tiene tres variantes (VP8X, VP8 con pérdida y VP8L sin pérdida); cada una guarda el tamaño de otra forma. */
    private static int[] medidaWebp(byte[] d) {
        if (d.length < 30) return null;
        String bloque = "" + (char) d[12] + (char) d[13] + (char) d[14] + (char) d[15];
        int w, h;
        switch (bloque) {
            case "VP8X" -> { w = 1 + (u8(d, 24) | (u8(d, 25) << 8) | (u8(d, 26) << 16)); h = 1 + (u8(d, 27) | (u8(d, 28) << 8) | (u8(d, 29) << 16)); }
            case "VP8 " -> {
                if (u8(d, 23) != 0x9D || u8(d, 24) != 0x01 || u8(d, 25) != 0x2A) return null;
                w = (u8(d, 26) | (u8(d, 27) << 8)) & 0x3FFF; h = (u8(d, 28) | (u8(d, 29) << 8)) & 0x3FFF;
            }
            case "VP8L" -> {
                if (u8(d, 20) != 0x2F) return null;
                w = 1 + ((u8(d, 21) | (u8(d, 22) << 8)) & 0x3FFF);
                h = 1 + (((u8(d, 22) >> 6) | (u8(d, 23) << 2) | ((u8(d, 24) & 0x0F) << 10)) & 0x3FFF);
            }
            default -> { return null; }
        }
        return w > 0 && h > 0 ? new int[]{w, h} : null;
    }
}
