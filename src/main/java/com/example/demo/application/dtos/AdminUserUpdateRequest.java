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

    // Cuentas demo — para marcas del Club de Beneficios
    private Boolean isDemo;
    private String demoMarca;
    private String demoNivel;
    private Integer demoVotaciones;
    private Integer demoComentarios;
    private Integer demoPublicaciones;
    private Integer demoSeguidores;
    private Integer demoSeguidos;
    private String demoAvatarUrl;
    private String demoBannerUrl;
}