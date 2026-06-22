package com.example.demo.application.services;

import com.example.demo.domain.notification.Notification;
import com.example.demo.domain.notification.NotificationRepository;
import com.example.demo.domain.notification.NotificationType;
import com.example.demo.domain.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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
    }

    @Transactional
    public void crearComentarioEliminado(User receptor, Long movieId, String movieTitle) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.COMMENT_REMOVED);
        n.setMessage("Tu comentario en " + movieTitle + " fue reportado y eliminado por no cumplir con nuestras normas de convivencia.");
        n.setMovieId(movieId);
        n.setMovieTitle(movieTitle);
        notificationRepository.save(n);
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
    }

    @Transactional
    public void crearAscensoInsignia(User receptor,
                                     com.example.demo.domain.user.UserLevel nivelAnterior,
                                     com.example.demo.domain.user.UserLevel nivelNuevo) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.INSIGNIA_ASCENSO);
        n.setMessage("🎉 ¡Felicitaciones! Completaste todos los desafíos y ahora sos "
                + nivelNuevo.getDisplayName()
                + ". Ingresá a Mi Cuenta para ver tu nueva insignia.");
        notificationRepository.save(n);
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
    }

    // =============================================
    // NUEVO PREMIO COMÚN — notificar a todos
    // =============================================
    @Transactional
    public void crearNuevoPremioComun(User receptor, String nombrePremio,
                                      int puntos, String rewardType) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.NEW_REWARD);

        String prefijo;
        switch (rewardType) {
            case "TICKET"        -> prefijo = "Nueva entrada disponible";
            case "MERCHANDISING" -> prefijo = "Nuevo premio disponible";
            case "DESCUENTO"     -> prefijo = "Nuevo descuento disponible";
            case "EXPERIENCIA"   -> prefijo = "Nueva experiencia disponible";
            default              -> prefijo = "Nuevo premio disponible";
        }

        n.setMessage("¡" + prefijo + ": " + nombrePremio
                + " — podés canjearlo ahora por " + puntos + " pts!");
        n.setReferenceType(rewardType);
        notificationRepository.save(n);
    }

    // =============================================
    // NUEVO PREMIO PREMIUM — discriminar premium vs no premium
    // =============================================
    @Transactional
    public void crearNuevoPremiumReward(User receptor, String nombrePremio,
                                        int puntos, String tipo, boolean esPremium) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName("Cinemarketer");
        n.setType(NotificationType.NEW_PREMIUM_REWARD);

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
    }
}
