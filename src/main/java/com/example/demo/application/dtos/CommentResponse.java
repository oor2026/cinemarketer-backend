package com.example.demo.application.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommentResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String content;
    private LocalDateTime createdAt;
    private String avatarUrl;
    private boolean reportedByMe;
    private boolean ownComment;

    // Reacciones
    private long bancoCount;
    private boolean bancadoByMe;
    private long merecePuntoCount;
    private boolean merecePuntoByMe;
    private boolean merecePuntoLocked; // true si el punto ya paso a disponible

    // Respuestas
    private long replyCount;
    private Boolean hasGif;
    private String gifUrl;
    private boolean spoiler;
    private LocalDateTime editedAt;
    private boolean canEdit; // true si aún está dentro de los 15 min
    private Integer pointsAwarded; // Puntos ganados por este comentario (0 si no correspondió)

    public CommentResponse(Long id, Long userId, String userName, String content,
                           LocalDateTime createdAt, String avatarUrl,
                           boolean reportedByMe, boolean ownComment) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.content = content;
        this.createdAt = createdAt;
        this.avatarUrl = avatarUrl;
        this.reportedByMe = reportedByMe;
        this.ownComment = ownComment;
        this.bancoCount = 0;
        this.bancadoByMe = false;
        this.merecePuntoCount = 0;
        this.merecePuntoByMe = false;
        this.merecePuntoLocked = false;
        this.replyCount = 0;
    }
}