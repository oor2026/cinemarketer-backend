package com.example.demo.application.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
 
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.mail.from:info@cinemarketer.com.ar}")
    private String mailFrom;

    @Value("${app.frontend.url:http://localhost:63342/cinemarketer-front/src}")
    private String frontendUrl;

    private static final String LOGO_URL = "https://cinemarketer.com.ar/assets/images/isologotipoMail.jpg";

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Template base ─────────────────────────────────────────────────────────

    private String buildHtml(String bodyContent) {
        return "<!DOCTYPE html>" +
                "<html lang='es'><head><meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>Cinemarketer</title></head>" +
                "<body style='margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,sans-serif;'>" +
                "<table width='100%' cellpadding='0' cellspacing='0' style='background-color:#f4f4f4;padding:30px 0;'>" +
                "<tr><td align='center'>" +
                "<table width='600' cellpadding='0' cellspacing='0' style='max-width:600px;width:100%;background-color:#ffffff;border-radius:8px;overflow:hidden;'>" +

                // Banner
                "<tr><td align='center' style='background-color:#ffffff;padding:28px 40px;border-bottom:1px solid #eeeeee;'>" +
                "<img src='" + LOGO_URL + "' alt='Cinemarketer' width='260' style='display:block;max-width:260px;height:auto;'/>" +
                "</td></tr>" +

                // Contenido
                "<tr><td style='padding:36px 40px 28px;color:#222222;font-size:15px;line-height:1.7;'>" +
                bodyContent +
                "</td></tr>" +

                // Footer
                "<tr><td align='center' style='background-color:#f9f9f9;padding:20px 40px;border-top:1px solid #eeeeee;'>" +
                "<p style='margin:0;font-size:12px;color:#999999;'>© 2026 Cinemarketer. Todos los derechos reservados.</p>" +
                "<p style='margin:6px 0 0;font-size:12px;color:#999999;'>" +
                "<a href='https://cinemarketer.com.ar' style='color:#e23232;text-decoration:none;'>cinemarketer.com.ar</a>" +
                "</p>" +
                "</td></tr>" +

                "</table>" +
                "</td></tr></table>" +
                "</body></html>";
    }

    private void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setFrom(mailFrom);
            helper.setSubject(subject);
            helper.setText(buildHtml(htmlBody), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Error al enviar email: " + e.getMessage(), e);
        }
    }

    private String btn(String url, String label) {
        return "<div style='text-align:center;margin:28px 0;'>" +
                "<a href='" + url + "' style='background-color:#e23232;color:#ffffff;text-decoration:none;" +
                "padding:13px 32px;border-radius:6px;font-size:15px;font-weight:bold;display:inline-block;'>" +
                label + "</a></div>";
    }

    // ── Métodos de envío ──────────────────────────────────────────────────────

    public void sendVerificationEmail(String to, String token) {
        String verificationUrl = baseUrl + "/api/auth/verify?token=" + token;
        String body =
                "<p>Hola,</p>" +
                        "<p>Gracias por registrarte en <strong>Cinemarketer</strong>. Por favor verificá tu cuenta haciendo clic en el siguiente botón:</p>" +
                        btn(verificationUrl, "Verificar mi cuenta") +
                        "<p style='font-size:13px;color:#888888;'>Si no creaste una cuenta, podés ignorar este mensaje.</p>";
        sendHtml(to, "Cinemarketer - Verificá tu cuenta", body);
    }

    public void sendEmailChangeVerification(String to, String token) {
        String verificationUrl = baseUrl + "/api/auth/verify?token=" + token;
        String body =
                "<p>Hola,</p>" +
                        "<p>Recibimos una solicitud para cambiar el email asociado a tu cuenta de <strong>Cinemarketer</strong>.</p>" +
                        "<p>Para confirmar tu nueva dirección de correo, hacé clic en el siguiente botón:</p>" +
                        btn(verificationUrl, "Confirmar nuevo email") +
                        "<p style='font-size:13px;color:#888888;'>Si no realizaste este cambio, te recomendamos ingresar a tu cuenta y revisar tu configuración.</p>";
        sendHtml(to, "Cinemarketer - Confirmá tu nuevo email", body);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String resetUrl = frontendUrl + "/reset-password.html?token=" + token;
        String body =
                "<p>Hola,</p>" +
                        "<p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en <strong>Cinemarketer</strong>.</p>" +
                        "<p>Para crear una nueva contraseña, hacé clic en el siguiente botón:</p>" +
                        btn(resetUrl, "Restablecer contraseña") +
                        "<p style='font-size:13px;color:#888888;'>Este enlace es válido por 24 horas. Si no solicitaste este cambio, podés ignorar este mensaje — tu contraseña actual seguirá siendo la misma.</p>";
        sendHtml(to, "Cinemarketer - Restablecer contraseña", body);
    }

    public void sendCommentRemovedEmail(String to, String userName, String commentContent, String adminReason) {
        String preview = commentContent.length() > 200 ? commentContent.substring(0, 200) + "..." : commentContent;
        String body =
                "<p>Hola <strong>" + userName + "</strong>,</p>" +
                        "<p>Te informamos que uno de tus comentarios en Cinemarketer fue eliminado por no cumplir con nuestras políticas de convivencia.</p>" +
                        "<div style='background-color:#f9f9f9;border-left:4px solid #e23232;padding:14px 18px;margin:20px 0;border-radius:4px;'>" +
                        "<p style='margin:0 0 6px;font-size:13px;color:#888888;'>Comentario eliminado:</p>" +
                        "<p style='margin:0;font-style:italic;color:#444444;'>\"" + preview + "\"</p>" +
                        "</div>" +
                        "<p><strong>Motivo:</strong> " + adminReason + "</p>" +
                        "<p style='font-size:13px;color:#888888;'>Si tenés alguna consulta al respecto, podés comunicarte desde el módulo <em>Mis Consultas</em> en tu cuenta.</p>";
        sendHtml(to, "Cinemarketer - Tu comentario fue eliminado", body);
    }

    public void sendAccountDeletionEmail(String to, String userName) {
        String body =
                "<p>Hola <strong>" + userName + "</strong>,</p>" +
                        "<p>Te confirmamos que tu cuenta en <strong>Cinemarketer</strong> ha sido eliminada exitosamente junto con todos tus datos.</p>" +
                        "<p>Si no realizaste esta acción o creés que fue un error, comunicate con nosotros a " +
                        "<a href='mailto:info@cinemarketer.com.ar' style='color:#e23232;'>info@cinemarketer.com.ar</a>.</p>" +
                        "<p>Fue un placer tenerte en nuestra comunidad. ¡Hasta pronto!</p>";
        sendHtml(to, "Cinemarketer - Tu cuenta fue eliminada", body);
    }

    public void sendRedemptionCompletedEmail(String to, String userName, String rewardName, String code) {
        String body =
                "<p>Hola <strong>" + userName + "</strong>,</p>" +
                        "<p>Tu premio <strong>\"" + rewardName + "\"</strong> ya fue entregado y está en tu poder.</p>" +
                        "<div style='background-color:#f9f9f9;border:1px solid #eeeeee;padding:16px 20px;margin:20px 0;border-radius:6px;text-align:center;'>" +
                        "<p style='margin:0 0 4px;font-size:13px;color:#888888;'>Código de canje</p>" +
                        "<p style='margin:0;font-size:22px;font-weight:bold;color:#222222;letter-spacing:2px;'>" + code + "</p>" +
                        "</div>" +
                        "<p>Gracias por participar en Cinemarketer. ¡Seguí acumulando puntos!</p>";
        sendHtml(to, "Cinemarketer - Tu premio fue entregado", body);
    }

    public void sendPremiumRedemptionEmail(String to, String userName, String rewardName, String code) {
        String body =
                "<p>Hola <strong>" + userName + "</strong>,</p>" +
                        "<p>¡Canjeaste exitosamente el premio premium <strong>\"" + rewardName + "\"</strong>!</p>" +
                        "<div style='background-color:#f9f9f9;border:1px solid #eeeeee;padding:16px 20px;margin:20px 0;border-radius:6px;text-align:center;'>" +
                        "<p style='margin:0 0 4px;font-size:13px;color:#888888;'>Tu código de canje</p>" +
                        "<p style='margin:0;font-size:22px;font-weight:bold;color:#222222;letter-spacing:2px;'>" + code + "</p>" +
                        "</div>" +
                        "<p>Nuestro equipo se pondrá en contacto para coordinar la entrega.</p>";
        sendHtml(to, "Cinemarketer - ¡Canjeaste un premio premium!", body);
    }

    public void sendDrawWinnerEmail(String to, String userName, String rewardName) {
        String body =
                "<p>Hola <strong>" + userName + "</strong>,</p>" +
                        "<p style='font-size:18px;font-weight:bold;color:#e23232;'>¡Felicitaciones!</p>" +
                        "<p>Fuiste seleccionado como ganador del sorteo <strong>\"" + rewardName + "\"</strong>.</p>" +
                        "<p>Nuestro equipo se pondrá en contacto con vos a la brevedad para coordinar la entrega del premio.</p>";
        sendHtml(to, "Cinemarketer - ¡Ganaste el sorteo!", body);
    }

    public void sendRedemptionConfirmationEmail(String to, String userName, String rewardName, String code) {
        String body =
                "<p>Hola <strong>" + userName + "</strong>,</p>" +
                        "<p>¡Canjeaste exitosamente el premio <strong>\"" + rewardName + "\"</strong>!</p>" +
                        "<div style='background-color:#f9f9f9;border:1px solid #eeeeee;padding:16px 20px;margin:20px 0;border-radius:6px;text-align:center;'>" +
                        "<p style='margin:0 0 4px;font-size:13px;color:#888888;'>Tu código de canje</p>" +
                        "<p style='margin:0;font-size:22px;font-weight:bold;color:#222222;letter-spacing:2px;'>" + code + "</p>" +
                        "</div>" +
                        "<p>Nuestro equipo procesará tu solicitud a la brevedad y te notificará cuando esté listo para retirar.</p>";
        sendHtml(to, "Cinemarketer - ¡Canjeaste un premio!", body);
    }

    public void sendPremiumRedemptionCompletedEmail(String to, String userName, String rewardName, String code) {
        String body =
                "<p>Hola <strong>" + userName + "</strong>,</p>" +
                        "<p>Tu premio premium <strong>\"" + rewardName + "\"</strong> ya fue entregado y está en tu poder.</p>" +
                        "<div style='background-color:#f9f9f9;border:1px solid #eeeeee;padding:16px 20px;margin:20px 0;border-radius:6px;text-align:center;'>" +
                        "<p style='margin:0 0 4px;font-size:13px;color:#888888;'>Código de canje</p>" +
                        "<p style='margin:0;font-size:22px;font-weight:bold;color:#222222;letter-spacing:2px;'>" + code + "</p>" +
                        "</div>" +
                        "<p>Gracias por ser parte de Cinemarketer Premium. ¡Seguí disfrutando los beneficios!</p>";
        sendHtml(to, "Cinemarketer - Tu premio premium fue entregado", body);
    }
}