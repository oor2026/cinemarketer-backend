package com.example.demo.application.services;

import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.domain.juego.JuegoCampania;
import com.example.demo.domain.juego.JuegoParticipante;
import com.example.demo.domain.juego.JuegoParticipanteRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Participantes de las campañas de juego.
 *
 * Con cuenta (CINEMARKETER): el participante se crea la primera vez que el
 * usuario inicia una partida, con su nombre y email de la cuenta.
 *
 * Sin registro (SIN_REGISTRO): la persona se anota con nombre y email. Recibe
 * un token "id.firma" (la firma es un HMAC del id con un secreto del servidor),
 * que el navegador guarda y manda en cada pedido. No se guarda en la base: si
 * vuelve desde otro dispositivo, escribe el mismo email y el mismo nombre y
 * recibe el mismo token.
 */
@Service
public class JuegoParticipanteService {

    private static final Logger log = LoggerFactory.getLogger(JuegoParticipanteService.class);

    private final JuegoParticipanteRepository participanteRepository;
    private final UserRepository userRepository;
    private final JuegoAdminService adminService;
    private final byte[] secreto;

    public JuegoParticipanteService(JuegoParticipanteRepository participanteRepository,
                                    UserRepository userRepository,
                                    JuegoAdminService adminService,
                                    @Value("${app.juegos.token-secret:}") String secreto) {
        this.participanteRepository = participanteRepository;
        this.userRepository = userRepository;
        this.adminService = adminService;
        if (secreto == null || secreto.isBlank()) {
            // Funciona igual, pero al reiniciar el back los jugadores sin registro
            // tienen que volver a escribir su email y nombre para seguir.
            log.warn("[Juegos] Falta app.juegos.token-secret: se usa un secreto temporal hasta el próximo reinicio.");
            byte[] azar = new byte[32];
            new SecureRandom().nextBytes(azar);
            this.secreto = azar;
        } else {
            this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
        }
    }

    public record AnotarseRequest(String nombre, String email, Boolean aceptaBases, Boolean aceptaNovedades) {}

    public record Anotado(String token, String nombre, boolean yaEstabaAnotado) {}

    // Datos para contactar al participante (mails y panel del admin).
    public record Contacto(String nombre, String email, String dni, String telefono, boolean conCuenta) {}

    // ===================================================================
    // Sin registro
    // ===================================================================
    @Transactional
    public Anotado anotarse(JuegoCampania c, AnotarseRequest req) {
        if (!c.sinRegistro()) throw new JuegoException(409, "Para esta actividad tenés que ingresar con tu cuenta de Cinemarketer");
        if (req == null) throw new JuegoException(400, "Faltan tus datos");
        String nombre = limpio(req.nombre());
        String email = limpio(req.email());
        if (nombre == null) throw new JuegoException(400, "Escribí tu nombre y apellido");
        if (nombre.length() > 120) throw new JuegoException(400, "El nombre es demasiado largo");
        if (email == null) throw new JuegoException(400, "Escribí tu email");
        if (email.length() > 200) throw new JuegoException(400, "El email es demasiado largo");
        if (!Boolean.TRUE.equals(req.aceptaBases())) throw new JuegoException(400, "Para participar tenés que aceptar las bases y condiciones");

        String clave = normalizarEmail(email);
        Optional<JuegoParticipante> existente = participanteRepository.findByCampaniaIdAndEmailNormalizado(c.getId(), clave);
        if (existente.isPresent()) {
            JuegoParticipante p = existente.get();
            // Vuelve desde otro dispositivo: tiene que escribir el mismo nombre.
            if (!normalizarNombre(p.getNombre()).equals(normalizarNombre(nombre))) {
                throw new JuegoException(409, "Ese email ya está participando con otro nombre. Escribí tu nombre igual que la primera vez.");
            }
            if (Boolean.TRUE.equals(req.aceptaNovedades()) && !Boolean.TRUE.equals(p.getAceptaNovedades())) {
                p.setAceptaNovedades(true);
                participanteRepository.save(p);
            }
            return new Anotado(token(p.getId()), p.getNombre(), true);
        }

        // Anotarse por primera vez, solo antes del cierre. Quien ya jugó puede volver
        // a identificarse después (por ejemplo, para ver o reclamar su premio).
        if (!JuegoCampania.PROGRAMADA.equals(c.getEstado()) && !JuegoCampania.ACTIVA.equals(c.getEstado())) {
            throw new JuegoException(409, "La actividad ya terminó: ya no se puede anotar gente nueva");
        }
        JuegoParticipante p = new JuegoParticipante();
        p.setCampaniaId(c.getId());
        p.setNombre(nombre);
        p.setEmail(email);
        p.setEmailNormalizado(clave);
        p.setAceptaNovedades(Boolean.TRUE.equals(c.getPedirNovedades()) && Boolean.TRUE.equals(req.aceptaNovedades()));
        LocalDateTime ahora = adminService.ahora();
        p.setAceptaBasesAt(ahora);
        p.setCreatedAt(ahora);
        try {
            p = participanteRepository.saveAndFlush(p);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Dos pedidos a la vez con el mismo email.
            throw new JuegoException(409, "Ya te anotaste. Recargá la página.");
        }
        return new Anotado(token(p.getId()), p.getNombre(), false);
    }

    // El participante del token, si es de esta campaña. Null si no hay token o no es válido.
    @Transactional(readOnly = true)
    public JuegoParticipante porToken(JuegoCampania c, String token) {
        Long id = idDelToken(token);
        if (id == null) return null;
        return participanteRepository.findById(id)
                .filter(p -> p.getCampaniaId().equals(c.getId()))
                .orElse(null);
    }

    // ===================================================================
    // Con cuenta de Cinemarketer
    // ===================================================================
    @Transactional(readOnly = true)
    public JuegoParticipante deUsuario(JuegoCampania c, User usuario) {
        if (usuario == null) return null;
        return participanteRepository.findByCampaniaIdAndUserId(c.getId(), usuario.getId()).orElse(null);
    }

    @Transactional
    public JuegoParticipante deUsuarioOCrear(JuegoCampania c, User usuario) {
        JuegoParticipante p = deUsuario(c, usuario);
        if (p != null) return p;
        p = new JuegoParticipante();
        p.setCampaniaId(c.getId());
        p.setUserId(usuario.getId());
        String nombre = limpio(usuario.getName());
        p.setNombre(nombre == null ? "Cinéfilo/a" : recortar(nombre, 120));
        String email = usuario.getEmail() == null ? "" : usuario.getEmail().trim();
        p.setEmail(recortar(email, 200));
        p.setEmailNormalizado(recortar(normalizarEmail(email), 200));
        p.setCreatedAt(adminService.ahora());
        try {
            return participanteRepository.saveAndFlush(p);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new JuegoException(409, "Ya tenés una partida abierta. Recargá la página.");
        }
    }

    // ===================================================================
    // Consultas
    // ===================================================================
    @Transactional(readOnly = true)
    public Map<Long, JuegoParticipante> participantes(Collection<Long> ids) {
        Map<Long, JuegoParticipante> mapa = new HashMap<>();
        if (ids.isEmpty()) return mapa;
        for (JuegoParticipante p : participanteRepository.findAllById(ids)) mapa.put(p.getId(), p);
        return mapa;
    }

    // Usuarios de los participantes que tienen cuenta (para avatar, DNI y teléfono).
    @Transactional(readOnly = true)
    public Map<Long, User> usuariosDe(Collection<JuegoParticipante> participantes) {
        Set<Long> ids = new HashSet<>();
        for (JuegoParticipante p : participantes) if (p.getUserId() != null) ids.add(p.getUserId());
        Map<Long, User> mapa = new HashMap<>();
        if (ids.isEmpty()) return mapa;
        for (User u : userRepository.findAllById(ids)) mapa.put(u.getId(), u);
        return mapa;
    }

    // Con cuenta se usan los datos actuales de la cuenta; sin cuenta, lo que escribió al anotarse.
    @Transactional(readOnly = true)
    public Contacto contacto(Long participanteId) {
        JuegoParticipante p = participanteRepository.findById(participanteId).orElse(null);
        if (p == null) return null;
        User u = p.getUserId() == null ? null : userRepository.findById(p.getUserId()).orElse(null);
        return contacto(p, u);
    }

    public static Contacto contacto(JuegoParticipante p, User u) {
        if (u != null) {
            return new Contacto(u.getName() != null && !u.getName().isBlank() ? u.getName() : p.getNombre(),
                    u.getEmail() != null ? u.getEmail() : p.getEmail(), u.getDni(), u.getPhone(), true);
        }
        return new Contacto(p.getNombre(), p.getEmail(), null, null, p.getUserId() != null);
    }

    // ===================================================================
    // Token
    // ===================================================================
    String token(Long participanteId) {
        return participanteId + "." + firma(participanteId);
    }

    Long idDelToken(String token) {
        if (token == null) return null;
        String t = token.trim();
        int punto = t.indexOf('.');
        if (punto <= 0 || punto == t.length() - 1) return null;
        long id;
        try {
            id = Long.parseLong(t.substring(0, punto));
        } catch (NumberFormatException e) {
            return null;
        }
        byte[] esperada = firma(id).getBytes(StandardCharsets.UTF_8);
        byte[] recibida = t.substring(punto + 1).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperada, recibida) ? id : null;
    }

    private String firma(Long id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            byte[] h = mac.doFinal(("juego-participante:" + id).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    // ===================================================================
    // Auxiliares
    // ===================================================================
    static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    // "  José  PÉREZ " y "jose perez" son el mismo nombre.
    static String normalizarNombre(String nombre) {
        if (nombre == null) return "";
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
