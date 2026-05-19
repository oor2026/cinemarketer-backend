package com.example.demo.application.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Data
public class RegisterRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    @Pattern(
            regexp = "^[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ]+(\\s[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ]+)*$",
            message = "El nombre solo puede contener letras y espacios simples entre palabras"
    )
    private String name;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Pattern(
            regexp = "^[^\\s@]+@(gmail|hotmail|outlook|yahoo|live|msn|icloud|me|mac|protonmail|proton|tutanota|gmx|yandex|zoho|fibertel|arnet|speedy|ciudad|uolsinectis|infovia|personal|claro|terra|bol|uol|oi|telmex)\\.[^\\s@]+$",
            message = "El proveedor de email no está permitido. Los proveedores aceptados son: Gmail, Hotmail, Outlook, Yahoo, Live, iCloud, ProtonMail, Tutanota, GMX, Yandex, Zoho, Fibertel, Arnet, Speedy, Ciudad, Personal, Claro. Para dominios privados o institucionales contactanos a info@cinemarketer.com.ar"
    )
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(
            regexp = "(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d@!_-]{8,}$",
            message = "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número. Solo se permiten letras, números y los caracteres @ ! - _"
    )
    private String password;

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "\\d{7,8}", message = "El DNI debe tener 7 u 8 dígitos numéricos")
    private String dni;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "\\+?[\\d\\s\\-]{8,20}", message = "Formato de teléfono inválido")
    private String phone;

}