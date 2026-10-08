package com.example.demo.application.services;

import com.example.demo.application.dtos.CredencialDto;
import com.example.demo.application.dtos.CredencialVerificacionDto;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Credencial Digital Cinéfila.
 *
 * Se genera solo si el usuario cumple TODOS los requisitos excluyentes, y la
 * fecha/hora de emisión la pone siempre el servidor. La credencial vence a los
 * pocos minutos (credencial.vigencia-minutos) y lleva un token firmado con
 * HMAC-SHA256 que el comercio verifica escaneando el QR: la verificación
 * consulta la base en ese momento, así que una cuenta dada de baja,
 * suspendida o con datos cambiados deja de validar sola, sin tabla de
 * revocaciones.
 *
 * Formato del token: base64url(payload) + "." + base64url(firma)
 * payload = "v1|idUsuario|emitidaEpochSeg|venceEpochSeg|huella"
 * huella  = primeros 16 hex de SHA-256(dni normalizado + "|" + nombre) —
 *           si el usuario cambia nombre o DNI después de generarla, la
 *           credencial vieja deja de validar.
 */
@Service
public class CredencialService {

    public static final ZoneId ZONA_AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter FORMATO_ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String VERSION = "v1";

    /** Un requisito excluyente que no se cumple: código estable para el front + texto para el usuario. */
    public record Faltante(String codigo, String mensaje) {}

    /** Resultado de pedir la credencial: o la credencial, o la lista de lo que falta. */
    public record Resultado(CredencialDto credencial, List<Faltante> faltantes) {
        public boolean generada() { return credencial != null; }
    }

    /** La cuenta no puede tener credencial (demo, suspendida, sin verificar). No se resuelve completando datos. */
    public static class CuentaNoHabilitadaException extends RuntimeException {
        public CuentaNoHabilitadaException(String mensaje) { super(mensaje); }
    }

    /** Falta configurar credencial.secret en el servidor. */
    public static class CredencialNoConfiguradaException extends RuntimeException {
        public CredencialNoConfiguradaException() { super("La credencial digital no está disponible en este momento."); }
    }

    private final UserRepository userRepository;
    private final TotemCinefiloService totemCinefiloService;
    private final byte[] clave;
    private final int vigenciaMinutos;
    private final String urlVerificacion;

    public CredencialService(UserRepository userRepository,
                             TotemCinefiloService totemCinefiloService,
                             @Value("${credencial.secret:}") String secreto,
                             @Value("${credencial.vigencia-minutos:10}") int vigenciaMinutos,
                             @Value("${credencial.url-verificacion:https://cinemarketer.com.ar/credencial-verificar}") String urlVerificacion) {
        this.userRepository = userRepository;
        this.totemCinefiloService = totemCinefiloService;
        // Sin secreto (o uno demasiado corto) la app arranca igual, pero la
        // credencial responde "no disponible": así olvidarse la variable en
        // Railway no tira abajo todo el backend.
        this.clave = (secreto == null || secreto.length() < 32) ? null : secreto.getBytes(StandardCharsets.UTF_8);
        this.vigenciaMinutos = Math.max(1, vigenciaMinutos);
        this.urlVerificacion = urlVerificacion;
    }

    // ==============================================
    // GENERAR (dueño logueado)
    // ==============================================

    public Resultado generar(User user) {
        if (clave == null) throw new CredencialNoConfiguradaException();
        validarCuentaHabilitada(user);

        Optional<TotemCinefiloService.Totem> totem = totemCinefiloService.calcular(user);
        List<Faltante> faltantes = requisitosFaltantes(user, totem.isPresent());
        if (!faltantes.isEmpty()) return new Resultado(null, faltantes);

        String dni = normalizarDni(user.getDni());
        Instant ahora = Instant.now();
        Instant vence = ahora.plusSeconds(vigenciaMinutos * 60L);
        String token = firmarToken(user.getId(), ahora, vence, huella(dni, user.getName()));
        UserLevel nivel = user.getLevel();

        CredencialDto dto = new CredencialDto(
                user.getName().trim(),
                user.getEffectiveAvatarUrl(),
                formatearDni(dni),
                nivel.name(),
                nivel.getDisplayName(),
                nivel.getEmoji(),
                totem.get().nombre(),
                totem.get().emoji(),
                FORMATO_ISO.format(ahora.atZone(ZONA_AR)),
                FORMATO_ISO.format(vence.atZone(ZONA_AR)),
                ahora.toEpochMilli(),
                vence.toEpochMilli(),
                ahora.toEpochMilli(),
                vigenciaMinutos,
                urlVerificacion + "?t=" + token
        );
        return new Resultado(dto, List.of());
    }

    /**
     * Requisitos excluyentes de datos. Aunque en la práctica siempre estén
     * cargados, se validan igual: sin todos, no hay credencial.
     * La foto NO es requisito: si no hay imagen, fotoUrl viaja en null y el
     * front muestra la inicial del nombre sobre un color.
     */
    public List<Faltante> requisitosFaltantes(User user, boolean tieneTotem) {
        List<Faltante> faltantes = new ArrayList<>();

        String nombre = user.getName() == null ? "" : user.getName().trim();
        if (nombre.isEmpty()) {
            faltantes.add(new Faltante("NOMBRE", "Cargá tu nombre completo."));
        } else if (nombre.split("\\s+").length < 2) {
            faltantes.add(new Faltante("NOMBRE", "Cargá tu nombre y apellido, tal como figuran en tu DNI."));
        }

        String dni = normalizarDni(user.getDni());
        if (dni == null) {
            faltantes.add(new Faltante("DNI", "Cargá tu número de DNI."));
        } else if (!dniValido(dni)) {
            faltantes.add(new Faltante("DNI", "Revisá tu número de DNI: debe tener 7 u 8 dígitos."));
        }

        if (user.getLevel() == null) {
            faltantes.add(new Faltante("INSIGNIA", "Tu insignia todavía no está asignada."));
        }

        if (!tieneTotem) {
            faltantes.add(new Faltante("TOTEM", "Todavía no tenés tótem cinéfilo: votá algunas películas para revelarlo."));
        }

        return faltantes;
    }

    private void validarCuentaHabilitada(User user) {
        if (user.isDemo()) {
            throw new CuentaNoHabilitadaException("La credencial digital no está disponible para cuentas de demostración.");
        }
        if (user.isSuspended() || !user.isActive()) {
            throw new CuentaNoHabilitadaException("Tu cuenta no está habilitada para generar la credencial digital.");
        }
        if (!user.isEmailVerified()) {
            throw new CuentaNoHabilitadaException("Verificá tu email para generar la credencial digital.");
        }
    }

    // ==============================================
    // VERIFICAR (comercio, sin login)
    // ==============================================

    public CredencialVerificacionDto verificar(String token) {
        String verificadaEn = FORMATO_ISO.format(Instant.now().atZone(ZONA_AR));
        if (clave == null) {
            return sinDatos("INVALIDA", "No se pudo verificar la credencial en este momento.", verificadaEn);
        }

        Optional<String[]> partes = leerToken(token);
        if (partes.isEmpty()) {
            return sinDatos("INVALIDA", "Este código no corresponde a una credencial de Cinemarketer.", verificadaEn);
        }

        String[] p = partes.get();
        long idUsuario;
        Instant emitida, vence;
        try {
            idUsuario = Long.parseLong(p[1]);
            emitida = Instant.ofEpochSecond(Long.parseLong(p[2]));
            vence = Instant.ofEpochSecond(Long.parseLong(p[3]));
        } catch (NumberFormatException e) {
            return sinDatos("INVALIDA", "Este código no corresponde a una credencial de Cinemarketer.", verificadaEn);
        }

        if (Instant.now().isAfter(vence)) {
            return sinDatos("VENCIDA", "La credencial venció. Pedile a la persona que la vuelva a generar desde la app.", verificadaEn);
        }

        Optional<User> encontrado = userRepository.findById(idUsuario);
        if (encontrado.isEmpty()) {
            return sinDatos("CUENTA_NO_HABILITADA", "La cuenta de esta credencial ya no existe en Cinemarketer.", verificadaEn);
        }
        User user = encontrado.get();
        if (user.isDemo() || user.isSuspended() || !user.isActive()) {
            return sinDatos("CUENTA_NO_HABILITADA", "La cuenta de esta credencial no está habilitada.", verificadaEn);
        }

        String dni = normalizarDni(user.getDni());
        if (dni == null || !p[4].equals(huella(dni, user.getName()))) {
            return sinDatos("DATOS_MODIFICADOS", "Los datos de la cuenta cambiaron después de generar la credencial. Pedile que la vuelva a generar.", verificadaEn);
        }

        UserLevel nivel = user.getLevel();
        // El tótem se muestra como está HOY: puede cambiar con los votos, así
        // que no forma parte de la huella ni invalida la credencial.
        Optional<TotemCinefiloService.Totem> totem = totemCinefiloService.calcular(user);
        return new CredencialVerificacionDto(
                "VALIDA",
                "Credencial válida.",
                user.getName().trim(),
                user.getEffectiveAvatarUrl(),
                formatearDni(dni),
                nivel == null ? null : nivel.getDisplayName(),
                nivel == null ? null : nivel.getEmoji(),
                totem.map(TotemCinefiloService.Totem::nombre).orElse(null),
                totem.map(TotemCinefiloService.Totem::emoji).orElse(null),
                FORMATO_ISO.format(emitida.atZone(ZONA_AR)),
                FORMATO_ISO.format(vence.atZone(ZONA_AR)),
                verificadaEn
        );
    }

    private CredencialVerificacionDto sinDatos(String estado, String mensaje, String verificadaEn) {
        return new CredencialVerificacionDto(estado, mensaje, null, null, null, null, null, null, null, null, null, verificadaEn);
    }

    // ==============================================
    // TOKEN
    // ==============================================

    private String firmarToken(Long idUsuario, Instant emitida, Instant vence, String huella) {
        String payload = String.join("|", VERSION, String.valueOf(idUsuario),
                String.valueOf(emitida.getEpochSecond()), String.valueOf(vence.getEpochSecond()), huella);
        String payloadB64 = b64(payload.getBytes(StandardCharsets.UTF_8));
        return payloadB64 + "." + b64(hmac(payloadB64));
    }

    /** Devuelve las 5 partes del payload solo si la firma es correcta. */
    private Optional<String[]> leerToken(String token) {
        if (token == null || token.length() > 300) return Optional.empty();
        int punto = token.indexOf('.');
        if (punto <= 0 || punto != token.lastIndexOf('.')) return Optional.empty();
        String payloadB64 = token.substring(0, punto);
        byte[] firmaRecibida;
        try {
            firmaRecibida = Base64.getUrlDecoder().decode(token.substring(punto + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // Comparación en tiempo constante
        if (!MessageDigest.isEqual(hmac(payloadB64), firmaRecibida)) return Optional.empty();
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String[] partes = payload.split("\\|", -1);
        if (partes.length != 5 || !VERSION.equals(partes[0])) return Optional.empty();
        return Optional.of(partes);
    }

    private byte[] hmac(String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(datos.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la credencial", e);
        }
    }

    private static String huella(String dni, String nombre) {
        try {
            String base = dni + "|" + (nombre == null ? "" : nombre.trim().toLowerCase());
            byte[] h = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }

    // ==============================================
    // DNI
    // ==============================================

    /** Deja solo los dígitos ("12.345.678" → "12345678"). null si no queda nada. */
    public static String normalizarDni(String dni) {
        if (dni == null) return null;
        String soloDigitos = dni.replaceAll("[^0-9]", "");
        return soloDigitos.isEmpty() ? null : soloDigitos;
    }

    public static boolean dniValido(String dniNormalizado) {
        return dniNormalizado != null && dniNormalizado.matches("\\d{7,8}");
    }

    /** "12345678" → "12.345.678" */
    public static String formatearDni(String dniNormalizado) {
        StringBuilder sb = new StringBuilder(dniNormalizado);
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }
}
