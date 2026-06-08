package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FollowDto {
    private Long id;
    private String name;
    private String avatarUrl;
    private String level;
    private String levelEmoji;
}