package com.example.demo.application.dtos;

import com.example.demo.domain.user.UserLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para agrupar avatares por nivel
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvatarsByLevelDto {
    private UserLevel level;
    private String levelDisplayName;
    private List<AvatarDto> avatars;
}