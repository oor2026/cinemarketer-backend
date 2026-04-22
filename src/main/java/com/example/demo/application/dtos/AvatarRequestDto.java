package com.example.demo.application.dtos;

import com.example.demo.domain.user.UserLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para crear o actualizar un avatar (admin)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvatarRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String category;

    private UserLevel requiredLevel;

    private Integer sortOrder;

    private Boolean active = true;

    private Boolean isDefault = false;
}