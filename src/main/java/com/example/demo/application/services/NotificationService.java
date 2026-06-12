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
}
