package com.example.demo.application.dtos;

import com.example.demo.domain.avatar.Avatar;
import com.example.demo.domain.user.UserLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para transferir datos de avatar
 * Versión simplificada para listados y galerías
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvatarDto {
    private Long id;
    private String name;
    private String category;
    private String imageUrl;
    private String thumbnailUrl;
    private UserLevel requiredLevel;
    private boolean isDefault;
    private int sortOrder;
    private boolean active;

    /**
     * Constructor desde entidad Avatar
     */
    public static AvatarDto fromEntity(Avatar avatar) {
        return new AvatarDto(
                avatar.getId(),
                avatar.getName(),
                avatar.getCategory(),
                avatar.getImageUrl(),
                avatar.getThumbnailUrl(),
                avatar.getRequiredLevel(),
                avatar.isDefault(),
                avatar.getSortOrder(),
                avatar.isActive()
        );
    }
}