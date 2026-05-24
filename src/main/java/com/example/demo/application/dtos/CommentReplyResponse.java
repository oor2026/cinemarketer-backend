package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CommentReplyResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String content;
    private LocalDateTime createdAt;
    private String avatarUrl;
    private boolean ownReply;
    private long bancoCount;
    private boolean bancadoByMe;
}
