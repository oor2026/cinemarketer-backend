package com.example.demo.application.dtos;

/**
 * Lo que ve el comercio al escanear el QR. Incluye el DNI completo, porque es
 * lo que el comercio coteja contra el DNI físico de la persona.
 * fotoUrl puede venir en null aunque la credencial sea válida (el usuario no
 * cargó imagen): en ese caso se muestra la inicial del nombre.
 *
 * estado:
 * - VALIDA: credencial vigente; vienen todos los datos.
 * - VENCIDA: pasó la vigencia; hay que pedirle al usuario que la regenere.
 * - DATOS_MODIFICADOS: el nombre o el DNI cambiaron después de generarla.
 * - CUENTA_NO_HABILITADA: la cuenta ya no existe, está suspendida o inactiva.
 * - INVALIDA: el código no es de Cinemarketer o fue adulterado.
 *
 * Fuera de VALIDA, los datos personales vienen en null.
 */
public record CredencialVerificacionDto(
        String estado,
        String mensaje,
        String nombre,
        String fotoUrl,
        String numeroCredencial,
        String insigniaNombre,
        String insigniaEmoji,
        String totemNombre,
        String totemEmoji,
        String emitidaEn,
        String venceEn,
        String verificadaEn
) {}
