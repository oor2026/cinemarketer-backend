package com.example.demo.application.dtos;

/**
 * Credencial Digital Cinéfila tal como la ve su dueño.
 * La genera el servidor: la fecha/hora de emisión y el vencimiento salen
 * siempre de acá, nunca del navegador.
 *
 * - fotoUrl: puede venir en null (no es requisito); el front muestra la
 *   inicial del nombre sobre un color.
 * - numeroCredencial: el DNI formateado (12.345.678).
 * - emitidaEnEpochMs / venceEnEpochMs / servidorAhoraEpochMs: en milisegundos,
 *   para que el front arme el reloj en vivo con la hora del servidor y no con
 *   la del teléfono.
 * - urlVerificacion: lo que se codifica en el QR (incluye el token firmado).
 */
public record CredencialDto(
        String nombre,
        String fotoUrl,
        String numeroCredencial,
        String insigniaCodigo,
        String insigniaNombre,
        String insigniaEmoji,
        String totemNombre,
        String totemEmoji,
        String emitidaEn,
        String venceEn,
        long emitidaEnEpochMs,
        long venceEnEpochMs,
        long servidorAhoraEpochMs,
        int vigenciaMinutos,
        String urlVerificacion
) {}
