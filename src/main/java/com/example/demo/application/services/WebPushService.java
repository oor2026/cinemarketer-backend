package com.example.demo.application.services;

import com.example.demo.domain.push.PushSubscription;
import com.example.demo.domain.push.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    @Value("${vapid.public-key}")
    private String vapidPublicKeyB64;

    @Value("${vapid.private-key}")
    private String vapidPrivateKeyB64;

    @Value("${vapid.subject}")
    private String vapidSubject;

    private final PushSubscriptionRepository pushSubscriptionRepository;

    public WebPushService(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    // Enviar push a un usuario específico
    public void sendToUser(Long userId, String title, String body, String icon) {
        List<PushSubscription> subs = pushSubscriptionRepository.findByUserId(userId);
        for (PushSubscription sub : subs) {
            try {
                sendPush(sub, title, body, icon);
            } catch (Exception e) {
                log.warn("❌ Error enviando push a suscripción {}: {}", sub.getId(), e.getMessage());
                // Si el endpoint es inválido (410 Gone), eliminar la suscripción
                if (e.getMessage() != null && e.getMessage().contains("410")) {
                    pushSubscriptionRepository.delete(sub);
                }
            }
        }
    }

    // Enviar push a todos los usuarios
    public void sendToAll(String title, String body, String icon) {
        List<PushSubscription> subs = pushSubscriptionRepository.findAll();
        for (PushSubscription sub : subs) {
            try {
                sendPush(sub, title, body, icon);
            } catch (Exception e) {
                log.warn("❌ Error enviando push a suscripción {}: {}", sub.getId(), e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("410")) {
                    pushSubscriptionRepository.delete(sub);
                }
            }
        }
    }

    private void sendPush(PushSubscription sub, String title, String body, String icon) throws Exception {
        String payload = String.format(
                "{\"title\":\"%s\",\"body\":\"%s\",\"icon\":\"%s\"}",
                escapeJson(title), escapeJson(body), icon != null ? icon : "/assets/images/icon-192.png"
        );

        byte[] encryptedPayload = encrypt(payload, sub.getP256dh(), sub.getAuth());
        String vapidToken = buildVapidToken(sub.getEndpoint());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sub.getEndpoint()))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Encoding", "aes128gcm")
                .header("Authorization", "vapid t=" + vapidToken + ",k=" + vapidPublicKeyB64)
                .header("TTL", "86400")
                .POST(HttpRequest.BodyPublishers.ofByteArray(encryptedPayload))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 410) {
            throw new RuntimeException("410 subscription expired");
        }
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Push failed: " + response.statusCode() + " " + response.body());
        }

        log.info("✅ Push enviado a {}: {}", sub.getEndpoint().substring(0, 40) + "...", title);
    }

    private byte[] encrypt(String payload, String p256dhB64, String authB64) throws Exception {
        // Decodificar claves del cliente
        byte[] clientPublicKeyBytes = base64UrlDecode(p256dhB64);
        byte[] authSecret = base64UrlDecode(authB64);

        // Generar par de claves efímeras del servidor
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("prime256v1"));
        KeyPair ephemeralKeyPair = keyGen.generateKeyPair();

        // Reconstruir clave pública del cliente
        KeyFactory kf = KeyFactory.getInstance("EC");
        ECPublicKey clientPublicKey = (ECPublicKey) kf.generatePublic(
                new X509EncodedKeySpec(toSpki(clientPublicKeyBytes))
        );

        // ECDH
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ephemeralKeyPair.getPrivate());
        ka.doPhase(clientPublicKey, true);
        byte[] sharedSecret = ka.generateSecret();

        // Obtener bytes de la clave pública efímera
        byte[] ephemeralPublicKeyBytes = getRawPublicKey((ECPublicKey) ephemeralKeyPair.getPublic());

        // HKDF para derivar claves
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        byte[] prk = hkdfExtract(authSecret, sharedSecret);
        byte[] keyInfo = buildInfo("aesgcm", clientPublicKeyBytes, ephemeralPublicKeyBytes);
        byte[] contentEncryptionKey = hkdfExpand(prk, keyInfo, 16);

        byte[] nonceInfo = buildInfo("nonce", clientPublicKeyBytes, ephemeralPublicKeyBytes);
        byte[] nonce = hkdfExpand(prk, nonceInfo, 12);

        // Cifrar payload con AES-GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentEncryptionKey, "AES"),
                new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        // Construir mensaje aes128gcm
        byte[] ephPubKeyBytes = ephemeralPublicKeyBytes;
        ByteBuffer result = ByteBuffer.allocate(
                salt.length + 4 + 1 + ephPubKeyBytes.length + ciphertext.length
        );
        result.put(salt);
        result.putInt(4096); // record size
        result.put((byte) ephPubKeyBytes.length);
        result.put(ephPubKeyBytes);
        result.put(ciphertext);
        return result.array();
    }

    private String buildVapidToken(String endpoint) throws Exception {
        String audience = getAudience(endpoint);
        long exp = Instant.now().getEpochSecond() + 12 * 3600;

        String header = base64UrlEncode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes());
        String claims = base64UrlEncode(
                String.format("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}", audience, exp, vapidSubject)
                        .getBytes(StandardCharsets.UTF_8)
        );
        String signingInput = header + "." + claims;

        // Cargar clave privada VAPID
        byte[] privateKeyBytes = Base64.getDecoder().decode(
                vapidPrivateKeyB64.replace('-', '+').replace('_', '/')
        );
        KeyFactory kf = KeyFactory.getInstance("EC");
        ECPrivateKey privateKey = (ECPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] derSignature = sig.sign();

        // Convertir DER a raw (r+s de 32 bytes cada uno)
        byte[] rawSignature = derToRaw(derSignature);
        return signingInput + "." + base64UrlEncode(rawSignature);
    }

    // ── Helpers criptográficos ────────────────────────────────────────────────

    private byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] t = new byte[0];
        byte[] okm = new byte[length];
        int offset = 0;
        byte counter = 1;
        while (offset < length) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update(counter++);
            t = mac.doFinal();
            int toCopy = Math.min(t.length, length - offset);
            System.arraycopy(t, 0, okm, offset, toCopy);
            offset += toCopy;
        }
        return okm;
    }

    private byte[] buildInfo(String type, byte[] clientKey, byte[] serverKey) {
        String prefix = "Content-Encoding: " + type + "\0P-256\0";
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(
                prefixBytes.length + 2 + clientKey.length + 2 + serverKey.length
        );
        buf.put(prefixBytes);
        buf.putShort((short) clientKey.length);
        buf.put(clientKey);
        buf.putShort((short) serverKey.length);
        buf.put(serverKey);
        return buf.array();
    }

    private byte[] toSpki(byte[] rawPublicKey) {
        // Prefijo SPKI para EC prime256v1 (04 = uncompressed point)
        byte[] spkiHeader = Base64.getDecoder().decode(
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE"
        );
        // El header ya incluye los primeros bytes, reemplazar los últimos 64 bytes
        byte[] result = new byte[spkiHeader.length - 1 + rawPublicKey.length];
        System.arraycopy(spkiHeader, 0, result, 0, spkiHeader.length - 1);
        System.arraycopy(rawPublicKey, 0, result, spkiHeader.length - 1, rawPublicKey.length);
        return result;
    }

    private byte[] getRawPublicKey(ECPublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        // Los últimos 65 bytes son el punto EC sin comprimir (04 + x + y)
        byte[] raw = new byte[65];
        System.arraycopy(encoded, encoded.length - 65, raw, 0, 65);
        return raw;
    }

    private byte[] derToRaw(byte[] der) {
        // Parsear DER SEQUENCE → extraer r y s como 32 bytes cada uno
        int rLen = der[3];
        int rOffset = 4 + (rLen > 32 ? 1 : 0); // skip leading zero if present
        int actualRLen = Math.min(rLen, 32);

        int sOffset = 4 + rLen + 2 + (der[4 + rLen + 1] > 32 ? 1 : 0);
        int sLen = der[4 + rLen + 1];
        int actualSLen = Math.min(sLen, 32);

        byte[] raw = new byte[64];
        System.arraycopy(der, rOffset, raw, 32 - actualRLen, actualRLen);
        System.arraycopy(der, sOffset, raw, 64 - actualSLen, actualSLen);
        return raw;
    }

    private String getAudience(String endpoint) throws Exception {
        URI uri = new URI(endpoint);
        return uri.getScheme() + "://" + uri.getHost();
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}