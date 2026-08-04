package com.example.demo.application.services;

import com.example.demo.domain.notification.Notification;
import com.example.demo.domain.notification.NotificationRepository;
import com.example.demo.domain.notification.NotificationType;
import com.example.demo.domain.user.User;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WebPushService webPushService;

    private static final String ICON = "/assets/images/icon-192.png";

    public NotificationService(NotificationRepository notificationRepository,
                               @Lazy WebPushService webPushService) {
        this.notificationRepository = notificationRepository;
        this.webPushService = webPushService;
    }

    @Transactional
    public void crearBanco(User receptor, String actorName, Long movieId, String movieTitle, Long commentId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.BANCO);
        n.setMessage(actorName + " bancó tu comentario en " + movieTitle);
        n.setMovieId(movieId);
        n.setMovieTitle(movieTitle);
        n.setCommentId(commentId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "👍 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearComentarioEnPublicacion(User receptor, String actorName, Long publicationId, Long comentarioId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.PUB_COMENTARIO);
        n.setMessage(actorName + " comentó tu publicación en Comunidad");
        n.setPublicationId(publicationId);
        n.setCommentId(comentarioId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "💬 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearRespuestaEnComentarioPublicacion(User receptor, String actorName, Long publicationId, Long comentarioId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.PUB_RESPUESTA);
        n.setMessage(actorName + " respondió tu comentario en una publicación de Comunidad");
        n.setPublicationId(publicationId);
        n.setCommentId(comentarioId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "💬 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearBancoComentarioPublicacion(User receptor, String actorName, Long publicationId, Long comentarioId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.PUB_BANCO_COMENTARIO);
        n.setMessage(actorName + " bancó tu comentario en una publicación de Comunidad");
        n.setPublicationId(publicationId);
        n.setCommentId(comentarioId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "👍 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearBancoPublicacion(User receptor, String actorName, Long publicationId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.PUB_BANCO);
        n.setMessage(actorName + " bancó tu publicación en Comunidad");
        n.setPublicationId(publicationId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "👍 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearMerecePunto(User receptor, String actorName, Long movieId, String movieTitle, Long commentId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.MERECE_PUNTO);
        n.setMessage(actorName + " consideró que tu comentario merece un punto en " + movieTitle);
        n.setMovieId(movieId);
        n.setMovieTitle(movieTitle);
        n.setCommentId(commentId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "⭐ Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearMerecePuntoPublicacion(User receptor, String actorName, Long pubId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.PUB_MERECE_PUNTO);
        n.setMessage(actorName + " consideró que tu publicación en Comunidad merece un punto");
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "⭐ Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearPublicacionOculta(User receptor, String reason, Long pubId, String pubTitle) {
        String tituloDisplay = (pubTitle != null && !pubTitle.isBlank())
                ? "\"" + pubTitle + "\"" : "(sin título)";
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.COMMENT_REMOVED);
        n.setMessage("Tu publicación " + tituloDisplay + " en Comunidad fue ocultada por moderación. Motivo: " + reason);
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🚫 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearPublicacionAprobada(User receptor, Long pubId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.PUB_APROBADA);
        n.setMessage("Tu publicación fue aprobada, ya la podés ver en Comunidad.");
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "✅ Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearPublicacionPendienteRevision(User receptor, Long pubId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.PUB_PENDIENTE_REVISION);
        n.setMessage("Tu publicación está pendiente de revisión. En caso de no violar nuestras normas de convivencia " +
                "la misma será publicada, en caso contrario no.");
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "⏳ Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    // Video agregado por edición a una publicación que ya estaba viva —
    // a diferencia de crearPublicacionAprobada, el resto de la publicación
    // (comentarios, puntos, engagement) nunca estuvo en riesgo.
    @Transactional
    public void crearPublicacionRechazadaPorDuracion(User receptor, Long pubId, int maxSegundos) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.VIDEO_RECHAZADO);
        n.setMessage("Tu publicación no pudo publicarse porque el video supera los " + maxSegundos +
                " segundos permitidos. Podés volver a intentarlo con un video de menor duración.");
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🚫 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearVideoRechazadoPorDuracion(User receptor, Long pubId, int maxSegundos) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.VIDEO_RECHAZADO);
        n.setMessage("Tu video no pudo agregarse porque supera los " + maxSegundos +
                " segundos permitidos. El resto de tu publicación sigue visible con normalidad.");
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🚫 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearVideoAprobado(User receptor, Long pubId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.VIDEO_APROBADO);
        n.setMessage("Tu video fue aprobado, ya lo podés ver en tu publicación.");
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "✅ Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearVideoRechazado(User receptor, Long pubId, String reason) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.VIDEO_RECHAZADO);
        n.setMessage("Tu video no pasó la revisión y fue retirado. Motivo: " + reason +
                ". El resto de tu publicación sigue visible con normalidad.");
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🚫 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearVideoPendienteRevision(User receptor, Long pubId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.VIDEO_PENDIENTE_REVISION);
        n.setMessage("Tu video está pendiente de revisión. El resto de tu publicación sigue visible con normalidad. " +
                "En caso de no violar nuestras normas de convivencia, el video será publicado; en caso contrario no.");
        n.setPublicationId(pubId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "⏳ Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearComentarioEliminado(User receptor, Long movieId, String movieTitle, String contenido, String tipoLabel) {
        String contenidoCorto = (contenido != null && contenido.length() > 150)
                ? contenido.substring(0, 150) + "..." : contenido;
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.COMMENT_REMOVED);
        n.setMessage("Tu " + tipoLabel + " en la película \"" + movieTitle + "\" fue reportado y eliminado por no cumplir " +
                "con nuestras normas de convivencia.\n\n\"" + contenidoCorto + "\"");
        n.setMovieId(movieId);
        n.setMovieTitle(movieTitle);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🚫 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearComentarioPublicacionEliminado(User receptor, Long publicationId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.COMMENT_REMOVED);
        n.setMessage("Tu comentario en una publicación de Comunidad fue reportado y eliminado por no cumplir con nuestras normas de convivencia.");
        n.setPublicationId(publicationId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🚫 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearComentarioPublicacionEliminado(User receptor, Long publicationId, String publicationTitle, String contenido) {
        String tituloDisplay = (publicationTitle != null && !publicationTitle.isBlank())
                ? "\"" + publicationTitle + "\"" : "(sin título)";
        String contenidoCorto = (contenido != null && contenido.length() > 150)
                ? contenido.substring(0, 150) + "..." : contenido;
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.COMMENT_REMOVED);
        n.setMessage("Tu comentario en la publicación " + tituloDisplay + " fue eliminado por no cumplir " +
                "con nuestras normas de convivencia.\n\n\"" + contenidoCorto + "\"");
        n.setPublicationId(publicationId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🚫 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearReply(User receptor, String actorName, Long movieId, String movieTitle, Long commentId, Long replyId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.REPLY);
        n.setMessage(actorName + " respondió tu comentario en " + movieTitle);
        n.setMovieId(movieId);
        n.setMovieTitle(movieTitle);
        n.setCommentId(commentId);
        n.setReplyId(replyId);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "💬 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearPuntosLiberados(User receptor, int acumulados, int cobrados, boolean pasóTecho) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.POINTS_RELEASED);
        String msg = "¡Gran trabajo! Acumulaste " + acumulados + " pts y cobraste " + cobrados + " pts.";
        if (pasóTecho) {
            msg += " Pasate a Premium para no tener tope.";
        }
        n.setMessage(msg);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🪙 Cinemarketer", msg, ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearRecomendacionCalificada(User receptor, String actorName, Long actorId,
                                             Long movieId, String movieTitle, int rating) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setActorId(actorId);
        n.setType(NotificationType.RECOMMENDATION_RATED);
        n.setMessage(actorName + " vio " + movieTitle + " que recomendaste y la calificó con " + rating + " estrella" + (rating == 1 ? "" : "s"));
        n.setMovieId(movieId);
        n.setMovieTitle(movieTitle);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "⭐ Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearAscensoInsignia(User receptor,
                                     com.example.demo.domain.user.UserLevel nivelAnterior,
                                     com.example.demo.domain.user.UserLevel nivelNuevo) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.INSIGNIA_ASCENSO);
        String msg = "🎉 ¡Felicitaciones! Completaste todos los desafíos y ahora sos "
                + nivelNuevo.getDisplayName()
                + ". Ingresá a Mi Cuenta para ver tu nueva insignia.";
        n.setMessage(msg);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🎉 ¡Subiste de insignia!", msg, ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearPremioInsignia(User receptor,
                                    com.example.demo.domain.user.UserLevel nivelNuevo,
                                    int puntos) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.INSIGNIA_PREMIO);
        n.setMessage("🎁 Por alcanzar el nivel " + nivelNuevo.getDisplayName()
                + " te regalamos " + puntos + " puntos disponibles. ¡Seguí disfrutando Cinemarketer!");
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🎁 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    @Transactional
    public void crearAdminGrantPoints(User receptor, int puntos, String tipo) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.ADMIN_GRANT_POINTS);
        String tipoLabel = "acumulados".equals(tipo) ? "acumulados" : "disponibles";
        n.setMessage("¡Cinemarketer ha decidido otorgarte " + puntos + " puntos " + tipoLabel + "! ¡Disfrutálos!");
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🪙 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    // =============================================
    // NUEVO PREMIO COMÚN — notificar a todos
    // =============================================
    @Transactional
    public void crearNuevoPremioComun(User receptor, String nombrePremio,
                                      int puntos, String rewardType, Long rewardId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.NEW_REWARD);
        n.setRewardId(rewardId);

        String prefijo;
        switch (rewardType) {
            case "TICKET"        -> prefijo = "Nueva entrada disponible";
            case "MERCHANDISING" -> prefijo = "Nuevo premio disponible";
            case "DESCUENTO"     -> prefijo = "Nuevo descuento disponible";
            case "EXPERIENCIA"   -> prefijo = "Nueva experiencia disponible";
            default              -> prefijo = "Nuevo premio disponible";
        }

        String msg = "¡" + prefijo + ": " + nombrePremio
                + " — podés canjearlo ahora por " + puntos + " pts!";
        n.setMessage(msg);
        n.setReferenceType(rewardType);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🎁 Cinemarketer", msg, ICON);
        } catch (Exception e) {}
    }

    // =============================================
    // NUEVO PREMIO PREMIUM — discriminar premium vs no premium
    // =============================================
    @Transactional
    public void crearNuevoPremiumReward(User receptor, String nombrePremio,
                                        int puntos, String tipo, boolean esPremium, Long rewardId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.NEW_PREMIUM_REWARD);
        n.setRewardId(rewardId);

        String mensaje;
        if ("SORTEO".equals(tipo)) {
            mensaje = esPremium
                    ? "¡Nuevo sorteo exclusivo: " + nombrePremio + " — anotate para participar!"
                    : "¡Nuevo sorteo exclusivo: " + nombrePremio + " — suscribite a Premium y participá gratis!";
        } else {
            mensaje = esPremium
                    ? "¡Nuevo premio exclusivo para vos: " + nombrePremio + " — canjealo por " + puntos + " pts!"
                    : "¡Nuevo premio exclusivo: " + nombrePremio + " — suscribite a Premium para canjearlo!";
        }

        n.setMessage(mensaje);
        n.setReferenceType(tipo);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "⭐ Cinemarketer", mensaje, ICON);
        } catch (Exception e) {}
    }

    // =============================================
    // GANADOR DE SORTEO
    // =============================================
    @Transactional
    public void crearGanadorSorteo(User receptor, String nombreSorteo) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.DRAW_WINNER);
        String msg = "¡Ganaste el sorteo: " + nombreSorteo + "! El equipo de Cinemarketer se contactará para coordinar la entrega.";
        n.setMessage(msg);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🏆 ¡Ganaste un sorteo!", msg, ICON);
        } catch (Exception e) {}
    }

    // =============================================
    // VENCIMIENTO PREMIUM
    // =============================================
    @Transactional
    public void crearPremiumVencimientoProximo(User receptor, int diasRestantes) {
        NotificationType type = diasRestantes <= 1
                ? NotificationType.PREMIUM_EXPIRING_TOMORROW
                : NotificationType.PREMIUM_EXPIRING_SOON;

        String msg = diasRestantes <= 1
                ? "⚠️ Tu suscripción Premium vence mañana. ¡Renovála para no perder tus beneficios!"
                : "⏰ Tu suscripción Premium vence en " + diasRestantes + " días. ¡Renovála para no perder tus beneficios!";

        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(type);
        n.setMessage(msg);
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "⏰ Cinemarketer Premium", msg, ICON);
        } catch (Exception e) {}
    }

    // =============================================
    // DESCALIFICACIÓN DE GANADOR
    // =============================================
    @Transactional
    public void crearNotifDescalificado(User receptor, String nombreSorteo) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.DRAW_WINNER);
        n.setMessage("Lamentablemente no pudimos coordinar la entrega de tu premio del sorteo \"" + nombreSorteo + "\". El premio fue reasignado a otro participante. ¡Seguí participando!");
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "😕 Cinemarketer", n.getMessage(), ICON);
        } catch (Exception e) {}
    }

    // =============================================
    // NUEVO GANADOR POR SUPLENCIA
    // =============================================
    @Transactional
    public void crearNotifNuevoGanador(User receptor, String nombreSorteo) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.DRAW_WINNER);
        n.setMessage("¡Felicitaciones! El ganador original no pudo coordinar la entrega del sorteo \"" + nombreSorteo + "\". ¡Fuiste seleccionado/a como nuevo ganador/a! Nuestro equipo se contactará con vos.");
        notificationRepository.save(n);

        // Web Push
        try {
            webPushService.sendToUser(receptor.getId(),
                    "🏆 ¡Ganaste el sorteo!", n.getMessage(), ICON);
        } catch (Exception e) {}
    }
}