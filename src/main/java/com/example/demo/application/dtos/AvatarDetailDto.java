package com.example.demo.application.dtos;

import com.example.demo.domain.avatar.Avatar;
import com.example.demo.domain.user.UserLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para vista detallada de avatar (admin)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvatarDetailDto {
    private Long id;
    private String name;
    private String category;
    private UserLevel requiredLevel;
    private String imageUrl;
    private String thumbnailUrl;
    private boolean active;
    private boolean isDefault;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AvatarDetailDto fromEntity(Avatar avatar) {
        AvatarDetailDto dto = new AvatarDetailDto();
        dto.setId(avatar.getId());
        dto.setName(avatar.getName());
        dto.setCategory(avatar.getCategory());
        dto.setRequiredLevel(avatar.getRequiredLevel());
        dto.setImageUrl(avatar.getImageUrl());
        dto.setThumbnailUrl(avatar.getThumbnailUrl());
        dto.setActive(avatar.isActive());
        dto.setDefault(avatar.isDefault());
        dto.setSortOrder(avatar.getSortOrder());
        dto.setCreatedAt(avatar.getCreatedAt());
        dto.setUpdatedAt(avatar.getUpdatedAt());
        return dto;
    }
}