package com.example.demo.application.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.mail.from:info@cinemarketer.com.ar}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String to, String token) {
        String subject = "Cinemarketer - Verifica tu cuenta";
        String verificationUrl = baseUrl + "/api/auth/verify?token=" + token;
        String message = String.format(
                "Hola,\n\n" +
                        "Gracias por registrarte en Cinemarketer. Por favor verifica tu cuenta haciendo clic en el siguiente enlace:\n\n" +
                        "%s\n\n" +
                        "Si no creaste una cuenta, ignora este mensaje.\n\n" +
                        "Saludos,\nEquipo Cinemarketer",
                verificationUrl
        );

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setFrom(mailFrom);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
    }

    public void sendEmailChangeVerification(String to, String token) {
        String subject = "Cinemarketer - Confirmá tu nuevo email";
        String verificationUrl = baseUrl + "/api/auth/verify?token=" + token;
        String message = String.format(
                "Hola,\n\n" +
                        "Recibimos una solicitud para cambiar el email asociado a tu cuenta de Cinemarketer.\n\n" +
                        "Para confirmar tu nueva dirección de correo, hacé clic en el siguiente enlace:\n\n" +
                        "%s\n\n" +
                        "Si no realizaste este cambio, te recomendamos ingresar a tu cuenta y revisar tu configuración.\n\n" +
                        "Saludos,\nEquipo Cinemarketer",
                verificationUrl
        );

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setFrom(mailFrom);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String subject = "Cinemarketer - Restablecer contraseña";
        String resetUrl = baseUrl + "/reset-password.html?token=" + token;
        String message = String.format(
                "Hola,\n\n" +
                        "Recibimos una solicitud para restablecer la contraseña de tu cuenta en Cinemarketer.\n\n" +
                        "Para crear una nueva contraseña, hacé clic en el siguiente enlace:\n\n" +
                        "%s\n\n" +
                        "Este enlace es válido por 24 horas. Si no solicitaste este cambio, podés ignorar este mensaje — tu contraseña actual seguirá siendo la misma.\n\n" +
                        "Saludos,\nEquipo Cinemarketer",
                resetUrl
        );

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setFrom(mailFrom);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
    }

    public void sendCommentRemovedEmail(String to, String userName, String commentContent, String adminReason) {
        String subject = "Cinemarketer - Tu comentario fue eliminado";
        String message = String.format(
                "Hola %s,\n\n" +
                        "Te informamos que uno de tus comentarios en Cinemarketer fue eliminado por no cumplir con nuestras politicas de convivencia.\n\n" +
                        "Comentario eliminado:\n" +
                        "\"%s\"\n\n" +
                        "Motivo:\n" +
                        "%s\n\n" +
                        "Si tenes alguna consulta al respecto, podes responder desde el modulo Mis Consultas en tu cuenta.\n\n" +
                        "Saludos,\nEquipo Cinemarketer",
                userName,
                commentContent.length() > 200 ? commentContent.substring(0, 200) + "..." : commentContent,
                adminReason
        );

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setFrom(mailFrom);
        email.setSubject(subject);
        email.setText(message);
        mailSender.send(email);
    }
}