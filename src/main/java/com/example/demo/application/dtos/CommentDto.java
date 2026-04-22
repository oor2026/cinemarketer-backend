package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CommentDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String content;
    private LocalDateTime createdAt;
}