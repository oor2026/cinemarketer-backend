package com.example.demo.application.dtos;

import lombok.Data;
import java.util.List;

@Data
public class PublicProfileDto {

    // Identidad
    private Long id;
    private String nombre;
    private String avatarUrl;
    private String nivel;
    private String nivelEmoji;
    private String nivelDisplayName;
    private String miembroDesde;

    // Stats
    private long seguidores;
    private long siguiendo;
    private long totalVotaciones;
    private long totalComentarios;
    private boolean esSeguido; // ¿el usuario logueado ya lo sigue?
    private String bioTitulo;
    private String bioTexto;

    // Actividad
    private List<VotacionDto> ultimasVotaciones;
    private List<VotacionSerieDto> ultimasVotacionesSeries;
    private List<ComentarioPublicoDto> ultimosComentarios;
    private List<ComentarioSerieDto> ultimosComentariosSeries;

    // Privacidad y follow
    private boolean esPrivado;
    private String followStatus;
    private Long followId;
    private boolean bloqueado;
    private boolean bloqueadoPorMi;

    @Data
    public static class VotacionDto {
        private Long movieId;
        private String movieTitle;
        private String posterPath;
        private String voto; // LIKE o DISLIKE
    }

    @Data
    public static class VotacionSerieDto {
        private Long seriesId;
        private String seriesTitle;
        private String posterPath;
        private String voto; // LIKE o DISLIKE
    }

    @Data
    public static class ComentarioPublicoDto {
        private Long commentId;
        private Long movieId;
        private String movieTitle;
        private String posterPath;
        private String contenido;
        private boolean spoiler;
        private String fechaRelativa;
        private int bancoCount;
        private int merecePuntoCount;
        private int replyCount;
    }

    @Data
    public static class ComentarioSerieDto {
        private Long commentId;
        private Long seriesId;
        private String seriesTitle;
        private String posterPath;
        private String contenido;
        private boolean spoiler;
        private String fechaRelativa;
        private int bancoCount;
        private int merecePuntoCount;
        private int replyCount;
    }
}