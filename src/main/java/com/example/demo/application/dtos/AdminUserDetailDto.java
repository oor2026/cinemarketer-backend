package com.example.demo.application.dtos;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminUserDetailDto {

    // Personal
    private String nombre;
    private String email;
    private String dni;
    private String telefono;
    private boolean emailVerificado;
    private boolean googleAuth;
    private LocalDateTime creadoEn;
    private LocalDateTime ultimoAcceso;

    // Cuenta
    private String rol;
    private String estado;
    private boolean premium;
    private String nivel;

    // Puntos
    private int puntosDisponibles;
    private int puntosAcumulados;
    private int puntosCanjeadosHistorico;

    // Actividad
    private long totalVotaciones;
    private long totalComentarios;
    private PremiosDto premios;

    @Data
    public static class PremiosDto {
        private long totalCanjeados;
        private long entradas;
        private long merchandising;
        private List<PremioCanjeadoDto> listado;
    }

    @Data
    public static class PremioCanjeadoDto {
        private String nombre;
        private String tipo;
        private LocalDateTime fecha;
    }
}