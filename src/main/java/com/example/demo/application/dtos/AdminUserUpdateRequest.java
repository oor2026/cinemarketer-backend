// AdminUserUpdateRequest.java
package com.example.demo.application.dtos;

import com.example.demo.domain.user.UserRole;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {
    private String name;
    private String email;
    private String dni;
    private String phone;
    private java.time.LocalDate birthDate;
    private String sexo;
    private String provincia;
    private String localidad;
    private UserRole role;
    private Integer totalPoints;
    private Boolean active;
}