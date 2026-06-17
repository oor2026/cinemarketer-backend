// AdminUserDto.java
package com.example.demo.application.dtos;

import com.example.demo.domain.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDto {
    private Long id;
    private String name;
    private String email;
    private String dni;
    private String phone;
    private UserRole role;
    private Integer totalPoints;
    private boolean active;
    private boolean suspended;
    private String suspensionReason;
    private LocalDateTime suspendedAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private boolean emailVerified;
    private int blockedByCount;
}