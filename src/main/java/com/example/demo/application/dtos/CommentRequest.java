package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class CommentRequest {
    private String content;
    private Long parentReplyId; // null si responde al comentario padre
    private String gifUrl;
}