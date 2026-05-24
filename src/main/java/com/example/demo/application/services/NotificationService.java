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
    public void crearReply(User receptor, String actorName, Long movieId, String movieTitle, Long commentId) {
        Notification n = new Notification();
        n.setUser(receptor);
        n.setActorName(actorName);
        n.setType(NotificationType.REPLY);
        n.setMessage(actorName + " respondió tu comentario en " + movieTitle);
        n.setMovieId(movieId);
        n.setMovieTitle(movieTitle);
        n.setCommentId(commentId);
        notificationRepository.save(n);
    }
}
