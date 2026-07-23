package com.example.demo.application.services;

import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.Set;

@Service
public class NombreReservadoService {

    private static final String PALABRA_BASE = "cinemarketer";

    // Distancia máxima de edición (letras distintas/faltantes/de más) para
    // considerar que alguien está intentando escribir "cinemarketer" con un
    // typo — cubre errores de tipeo reales sin tener que listarlos a mano.
    private static final int DISTANCIA_MAXIMA = 2;

    // Solo para casos que ni la normalización ni la distancia de edición
    // puedan cubrir (por ejemplo, si en el futuro aparece un apodo fonético
    // muy distinto en escritura pero que suena igual).
    private static final Set<String> VARIANTES_ADICIONALES = Set.of(
            // agregar acá casos puntuales que se detecten en el futuro
    );

    public boolean esNombreReservado(String nombre) {
        if (nombre == null) return false;

        String normalizado = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");

        // Colapsa letras repetidas seguidas ("kkkk" -> "k", "qqq" -> "q") —
        // cierra el truco de evasión de repetir una letra varias veces para
        // alargar la palabra y quedar fuera del rango de distancia de edición.
        String comprimido = normalizado.replaceAll("(.)\\1+", "$1");

        // 1) Coincidencia directa o como parte de un nombre más largo
        // ("cinemarketeroficial", "elcinemarketer", etc.)
        if (comprimido.contains(PALABRA_BASE)) {
            return true;
        }

        // 2) Typos: comparamos por distancia de edición, tanto contra el
        // nombre completo como contra cada "ventana" del mismo largo que
        // "cinemarketer" dentro de nombres más largos (para detectar el
        // mismo typo aunque venga con texto extra pegado alrededor).
        String sinNumeros = comprimido.replaceAll("[0-9]", "");
        if (contieneTypoDe(sinNumeros, PALABRA_BASE)) {
            return true;
        }

        // 3) Lista fija — resguardo para lo que quede fuera de lo anterior
        for (String reservado : VARIANTES_ADICIONALES) {
            String reservadoNormalizado = reservado.toLowerCase().replaceAll("[^a-z]", "");
            if (sinNumeros.contains(reservadoNormalizado)) {
                return true;
            }
        }

        return false;
    }

    private boolean contieneTypoDe(String texto, String palabra) {
        int largoPalabra = palabra.length();
        if (texto.length() <= largoPalabra + DISTANCIA_MAXIMA) {
            // Nombre corto: comparamos entero contra la palabra base.
            return distanciaEdicion(texto, palabra) <= DISTANCIA_MAXIMA;
        }
        // Nombre largo: probamos cada ventana del mismo largo (± tolerancia)
        // deslizándose sobre el texto, para no perder el typo si viene con
        // texto extra pegado alrededor.
        for (int i = 0; i + largoPalabra <= texto.length(); i++) {
            String ventana = texto.substring(i, i + largoPalabra);
            if (distanciaEdicion(ventana, palabra) <= DISTANCIA_MAXIMA) {
                return true;
            }
        }
        return false;
    }

    // Distancia de Levenshtein clásica — mínima cantidad de inserciones,
    // eliminaciones o sustituciones de un carácter para pasar de "a" a "b".
    private int distanciaEdicion(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + costo
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}