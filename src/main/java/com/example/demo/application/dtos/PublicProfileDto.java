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
    private Integer peliculaFavoritaId;
    private String peliculaFavoritaTitulo;
    private String peliculaFavoritaPoster;
    private Integer ultimaVistaCineId;
    private String ultimaVistaCineTitulo;
    private String ultimaVistaCinePoster;
    private Integer noMeCansoDeVerId;
    private String noMeCansoDeVerTitulo;
    private String noMeCansoDeVerPoster;
    private Integer noLaBancoId;
    private String noLaBancoTitulo;
    private String noLaBancoPoster;
    private Integer serieFavoritaId;
    private String serieFavoritaTitulo;
    private String serieFavoritaPoster;
    private Integer ultimaMaratonId;
    private String ultimaMaratonTitulo;
    private String ultimaMaratonPoster;
    private Integer noMeCansoDeVerSerieId;
    private String noMeCansoDeVerSerieTitulo;
    private String noMeCansoDeVerSeriePoster;
    private Integer noLaBancoSerieId;
    private String noLaBancoSerieTitulo;
    private String noLaBancoSeriePoster;
    // Pilar "Saber" — posición en el ranking de Trivia. null = nunca jugó esa trivia.
    private Integer rankingTriviaPeliculas;
    private Integer rankingTriviaSeries;
    private long totalRecomendadas;
    private long totalGuardadas;

    // Conteos individuales por Películas/Series — para los títulos "(N)"
    // de cada mazo (Votadas, Comentadas, Recomendadas, Guardadas)
    private long totalVotacionesPeliculas;
    private long totalVotacionesSeries;
    private long totalComentariosPeliculas;
    private long totalComentariosSeries;
    private long totalRecomendadasPeliculas;
    private long totalRecomendadasSeries;
    private long totalGuardadasPeliculas;
    private long totalGuardadasSeries;
    private List<com.example.demo.application.dtos.GenreScoreDto> adnCinefilo;
    private List<com.example.demo.application.dtos.GenreScoreDto> adnCinefiloSeries;

    // Actividad
    private List<VotacionDto> ultimasVotaciones;
    private List<VotacionSerieDto> ultimasVotacionesSeries;
    private List<ComentarioPublicoDto> ultimosComentarios;
    private List<ComentarioSerieDto> ultimosComentariosSeries;
    private List<RecomendadaDto> ultimasRecomendadas;
    private List<RecomendadaSerieDto> ultimasRecomendadasSeries;
    private List<GuardadaDto> ultimasGuardadas;
    private List<GuardadaSerieDto> ultimasGuardadasSeries;

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
    public static class RecomendadaDto {
        private Long movieId;
        private String movieTitle;
        private String posterPath;
        private Long veces;
    }

    @Data
    public static class RecomendadaSerieDto {
        private Long seriesId;
        private String seriesTitle;
        private String posterPath;
        private Long veces;
    }

    @Data
    public static class GuardadaDto {
        private Long movieId;
        private String movieTitle;
        private String posterPath;
    }

    @Data
    public static class GuardadaSerieDto {
        private Long seriesId;
        private String seriesTitle;
        private String posterPath;
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