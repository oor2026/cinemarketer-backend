package com.example.demo.application.dtos;

import com.example.demo.domain.user.UserLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para mostrar progreso de nivel junto con avatar
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLevelWithAvatarDto {
    private Long userId;
    private String userName;
    private UserLevel currentLevel;
    private String currentLevelDisplay;
    private String currentLevelEmoji;
    private UserLevel nextLevel;
    private String nextLevelDisplay;
    private String nextLevelEmoji;
    private double progress;
    private int pointsToNextLevel;
    private AvatarDto currentAvatar;
    private boolean canLevelUp;
}