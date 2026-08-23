package com.example.demo.web.controllers;

import com.example.demo.application.dtos.PublicProfileDto;
import com.example.demo.domain.comment.Comment;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.comment.CommentReactionRepository;
import com.example.demo.domain.comment.CommentReply;
import com.example.demo.domain.review.AdnCinefiloService;
import com.example.demo.domain.comment.CommentReplyRepository;
import com.example.demo.domain.comment.ReactionType;
import com.example.demo.domain.follow.UserFollow;
import com.example.demo.domain.follow.UserFollowRepository;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.domain.series.Series;
import com.example.demo.domain.review.Review;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.review.ReviewType;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.user.UserBlockRepository;
import com.example.demo.domain.series.SeriesComment;
import com.example.demo.domain.series.SeriesCommentRepository;
import com.example.demo.domain.series.SeriesCommentReactionRepository;
import com.example.demo.domain.series.SeriesCommentReplyRepository;
import com.example.demo.domain.comment.ReactionType;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class PublicProfileController {

    private final UserRepository userRepository;
    private final UserFollowRepository followRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;
    private final MovieRepository movieRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final CommentReplyRepository commentReplyRepository;
    private final UserBlockRepository userBlockRepository;
    private final com.example.demo.domain.series.SeriesReviewRepository seriesReviewRepository;
    private final com.example.demo.domain.series.SeriesRepository seriesRepository;
    private final SeriesCommentRepository seriesCommentRepository;
    private final SeriesCommentReactionRepository seriesCommentReactionRepository;
    private final SeriesCommentReplyRepository seriesCommentReplyRepository;
    private final AdnCinefiloService adnCinefiloService;
    private final com.example.demo.domain.series.AdnCinefiloSeriesService adnCinefiloSeriesService;
    private final com.example.demo.domain.recommendation.MovieRecommendationRepository movieRecommendationRepository;
    private final com.example.demo.domain.watchlist.WatchlistRepository watchlistRepository;
    private final com.example.demo.domain.recommendation.SeriesRecommendationRepository seriesRecommendationRepository;
    private final com.example.demo.domain.watchlist.SeriesWatchlistRepository seriesWatchlistRepository;

    public PublicProfileController(UserRepository userRepository,
                                   UserFollowRepository followRepository,
                                   ReviewRepository reviewRepository,
                                   CommentRepository commentRepository,
                                   MovieRepository movieRepository,
                                   CommentReactionRepository commentReactionRepository,
                                   CommentReplyRepository commentReplyRepository,
                                   UserBlockRepository userBlockRepository,
                                   com.example.demo.domain.series.SeriesReviewRepository seriesReviewRepository,
                                   com.example.demo.domain.series.SeriesRepository seriesRepository,
                                   SeriesCommentRepository seriesCommentRepository,
                                   SeriesCommentReactionRepository seriesCommentReactionRepository,
                                   SeriesCommentReplyRepository seriesCommentReplyRepository, AdnCinefiloService adnCinefiloService, com.example.demo.domain.series.AdnCinefiloSeriesService adnCinefiloSeriesService, com.example.demo.domain.recommendation.MovieRecommendationRepository movieRecommendationRepository, com.example.demo.domain.watchlist.WatchlistRepository watchlistRepository, com.example.demo.domain.recommendation.SeriesRecommendationRepository seriesRecommendationRepository, com.example.demo.domain.watchlist.SeriesWatchlistRepository seriesWatchlistRepository, com.example.demo.domain.recommendation.SeriesRecommendationRepository seriesRecommendationRepository1, com.example.demo.domain.watchlist.SeriesWatchlistRepository seriesWatchlistRepository1) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
        this.movieRepository = movieRepository;
        this.commentReactionRepository = commentReactionRepository;
        this.commentReplyRepository = commentReplyRepository;
        this.userBlockRepository = userBlockRepository;
        this.seriesReviewRepository = seriesReviewRepository;
        this.seriesRepository = seriesRepository;
        this.seriesCommentRepository = seriesCommentRepository;
        this.seriesCommentReactionRepository = seriesCommentReactionRepository;
        this.seriesCommentReplyRepository = seriesCommentReplyRepository;
        this.adnCinefiloService = adnCinefiloService;
        this.adnCinefiloSeriesService = adnCinefiloSeriesService;
        this.movieRecommendationRepository = movieRecommendationRepository;
        this.watchlistRepository = watchlistRepository;
        this.seriesRecommendationRepository = seriesRecommendationRepository1;
        this.seriesWatchlistRepository = seriesWatchlistRepository1;
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<?> getPublicProfile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        User me = userDetails != null
                ? userRepository.findByEmail(userDetails.getUsername()).orElse(null)
                : null;

        // ── Bloqueo ────────────────────────────────────────────
        boolean bloqueadoPorMi = me != null &&
                userBlockRepository.existsByBlockerIdAndBlockedId(me.getId(), target.getId());
        boolean meBloqueó = me != null &&
                userBlockRepository.existsByBlockerIdAndBlockedId(target.getId(), me.getId());

        PublicProfileDto dto = new PublicProfileDto();
        dto.setBloqueado(bloqueadoPorMi || meBloqueó);
        dto.setBloqueadoPorMi(bloqueadoPorMi);

        // ── Identidad ──────────────────────────────────────────
        dto.setId(target.getId());
        dto.setNombre(target.getName());
        dto.setAvatarUrl(target.getEffectiveAvatarUrl());
        dto.setNivel(target.getLevel() != null ? target.getLevel().name() : "AMATEUR");
        dto.setNivelEmoji(target.getLevel() != null ? target.getLevel().getEmoji() : "🟢");
        dto.setNivelDisplayName(target.getLevel() != null ? target.getLevel().getDisplayName() : "Amateur");
        dto.setMiembroDesde(formatMiembroDesde(target.getCreatedAt()));
        dto.setBioTitulo(target.getBioTitulo());
        dto.setBioTexto(target.getBioTexto());
        dto.setPeliculaFavoritaId(target.getPeliculaFavoritaId());
        Movie peliculaFavorita = resolverPelicula(target.getPeliculaFavoritaId());
        if (peliculaFavorita != null) {
            dto.setPeliculaFavoritaTitulo(peliculaFavorita.getTitle());
            dto.setPeliculaFavoritaPoster(peliculaFavorita.getPosterPath());
        }

        dto.setUltimaVistaCineId(target.getUltimaVistaCineId());
        Movie ultimaVistaCine = resolverPelicula(target.getUltimaVistaCineId());
        if (ultimaVistaCine != null) {
            dto.setUltimaVistaCineTitulo(ultimaVistaCine.getTitle());
            dto.setUltimaVistaCinePoster(ultimaVistaCine.getPosterPath());
        }

        dto.setNoMeCansoDeVerId(target.getNoMeCansoDeVerId());
        Movie noMeCansoDeVer = resolverPelicula(target.getNoMeCansoDeVerId());
        if (noMeCansoDeVer != null) {
            dto.setNoMeCansoDeVerTitulo(noMeCansoDeVer.getTitle());
            dto.setNoMeCansoDeVerPoster(noMeCansoDeVer.getPosterPath());
        }

        dto.setNoLaBancoId(target.getNoLaBancoId());
        Movie noLaBanco = resolverPelicula(target.getNoLaBancoId());
        if (noLaBanco != null) {
            dto.setNoLaBancoTitulo(noLaBanco.getTitle());
            dto.setNoLaBancoPoster(noLaBanco.getPosterPath());
        }

        dto.setSerieFavoritaId(target.getSerieFavoritaId());
        Series serieFavorita = resolverSerie(target.getSerieFavoritaId());
        if (serieFavorita != null) {
            dto.setSerieFavoritaTitulo(serieFavorita.getTitle());
            dto.setSerieFavoritaPoster(serieFavorita.getPosterPath());
        }

        dto.setUltimaMaratonId(target.getUltimaMaratonId());
        Series ultimaMaraton = resolverSerie(target.getUltimaMaratonId());
        if (ultimaMaraton != null) {
            dto.setUltimaMaratonTitulo(ultimaMaraton.getTitle());
            dto.setUltimaMaratonPoster(ultimaMaraton.getPosterPath());
        }

        dto.setNoMeCansoDeVerSerieId(target.getNoMeCansoDeVerSerieId());
        Series noMeCansoDeVerSerie = resolverSerie(target.getNoMeCansoDeVerSerieId());
        if (noMeCansoDeVerSerie != null) {
            dto.setNoMeCansoDeVerSerieTitulo(noMeCansoDeVerSerie.getTitle());
            dto.setNoMeCansoDeVerSeriePoster(noMeCansoDeVerSerie.getPosterPath());
        }

        dto.setNoLaBancoSerieId(target.getNoLaBancoSerieId());
        Series noLaBancoSerie = resolverSerie(target.getNoLaBancoSerieId());
        if (noLaBancoSerie != null) {
            dto.setNoLaBancoSerieTitulo(noLaBancoSerie.getTitle());
            dto.setNoLaBancoSeriePoster(noLaBancoSerie.getPosterPath());
        }
        dto.setAdnCinefilo(adnCinefiloService.calcular(target.getId()));
        dto.setAdnCinefiloSeries(adnCinefiloSeriesService.calcular(target.getId()));

        // Pilar "Saber" — posición en el ranking de Trivia (null = nunca jugó, no se muestra nada)
        dto.setRankingTriviaPeliculas(
                userRepository.findPosicionRankingTrivia(target.getId()).map(Long::intValue).orElse(null)
        );
        dto.setRankingTriviaSeries(
                userRepository.findPosicionRankingTriviaSeries(target.getId()).map(Long::intValue).orElse(null)
        );

        // ── Stats ──────────────────────────────────────────────
        dto.setSeguidores(followRepository.countByFollowingIdAndStatus(target.getId(), "ACCEPTED"));
        dto.setSiguiendo(followRepository.countByFollowerIdAndStatus(target.getId(), "ACCEPTED"));
        dto.setTotalVotaciones(reviewRepository.countByUserId(target.getId())
                + seriesReviewRepository.countByUserId(target.getId()));
        dto.setTotalComentarios(commentRepository.countByUserId(target.getId())
                + seriesCommentRepository.countByUserId(target.getId()));
        dto.setTotalRecomendadas(movieRecommendationRepository.countBySenderId(target.getId())
                + seriesRecommendationRepository.countBySenderId(target.getId()));
        dto.setTotalGuardadas(watchlistRepository.countByUserId(target.getId())
                + seriesWatchlistRepository.countByUserId(target.getId()));

        // Conteos individuales por tipo — para los títulos "(N)" de cada mazo
        dto.setTotalVotacionesPeliculas(reviewRepository.countByUserId(target.getId()));
        dto.setTotalVotacionesSeries(seriesReviewRepository.countByUserId(target.getId()));
        dto.setTotalComentariosPeliculas(commentRepository.countByUserId(target.getId()));
        dto.setTotalComentariosSeries(seriesCommentRepository.countByUserId(target.getId()));
        dto.setTotalRecomendadasPeliculas(movieRecommendationRepository.countBySenderId(target.getId()));
        dto.setTotalRecomendadasSeries(seriesRecommendationRepository.countBySenderId(target.getId()));
        dto.setTotalGuardadasPeliculas(watchlistRepository.countByUserId(target.getId()));
        dto.setTotalGuardadasSeries(seriesWatchlistRepository.countByUserId(target.getId()));
        dto.setEsSeguido(me != null &&
                followRepository.existsByFollowerIdAndFollowingId(me.getId(), target.getId()));

        // Privacidad
        dto.setEsPrivado(target.isPrivate());

        // Estado del follow del usuario actual hacia el target
        if (me != null) {
            followRepository.findByFollowerAndFollowing(me.getId(), target.getId())
                    .ifPresent(f -> {
                        dto.setFollowStatus(f.getStatus());
                        dto.setFollowId(f.getId());
                    });
        }

        // ── Últimas votaciones (máx 6) ─────────────────────────
        List<Review> reviews = reviewRepository
                .findByUserIdOrderByCreatedAtDesc(target.getId())
                .stream()
                .filter(r -> r.getReviewType() == ReviewType.MOVIE && r.getVote() != null)
                .limit(100)
                .toList();

        dto.setUltimasVotaciones(reviews.stream().map(r -> {
            PublicProfileDto.VotacionDto v = new PublicProfileDto.VotacionDto();
            v.setMovieId(r.getTargetId());
            v.setVoto(r.getVote().name());
            Optional<Movie> movie = movieRepository.findByTmdbId(r.getTargetId());
            movie.ifPresent(m -> {
                v.setMovieTitle(m.getTitle());
                v.setPosterPath(m.getPosterPath());
            });
            return v;
        }).toList());

        // ── Últimas votaciones de series (máx 6) ───────────────
        List<com.example.demo.domain.series.SeriesReview> seriesReviews = seriesReviewRepository
                .findByUserIdAndVoteIsNotNullOrderByCreatedAtDesc(target.getId())
                .stream()
                .limit(6)
                .toList();

        dto.setUltimasVotacionesSeries(seriesReviews.stream().map(r -> {
            PublicProfileDto.VotacionSerieDto v = new PublicProfileDto.VotacionSerieDto();
            v.setSeriesId(r.getSeriesId());
            v.setVoto(r.getVote().name());
            Optional<com.example.demo.domain.series.Series> serie = seriesRepository.findByTmdbId(r.getSeriesId());
            serie.ifPresent(s -> {
                v.setSeriesTitle(s.getTitle());
                v.setPosterPath(s.getPosterPath());
            });
            return v;
        }).toList());

        // Usamos el query correcto por userId
        List<Comment> userComments = commentRepository
                .findPublicByUserId(target.getId(), PageRequest.of(0, 5));

        dto.setUltimosComentarios(userComments.stream().map(c -> {
            PublicProfileDto.ComentarioPublicoDto cd = new PublicProfileDto.ComentarioPublicoDto();
            cd.setCommentId(c.getId());
            cd.setMovieId(c.getMovieId());
            cd.setSpoiler(c.isSpoiler());
            cd.setFechaRelativa(formatRelativa(c.getCreatedAt()));

            String contenido = c.isSpoiler() ? "— Comentario con spoiler —" : c.getContent();
            cd.setContenido(contenido);

            Optional<Movie> movie = movieRepository.findByTmdbId(c.getMovieId());
            movie.ifPresent(m -> {
                cd.setMovieTitle(m.getTitle());
                cd.setPosterPath(m.getPosterPath());
            });

            cd.setBancoCount((int) commentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.BANCO));
            cd.setMerecePuntoCount((int) commentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.MERECE_PUNTO));
            cd.setReplyCount((int) commentReplyRepository.countVisibleByCommentId(c.getId()));

            return cd;
        }).toList());

        // ── Últimos comentarios de series (máx 5) ──────────────
        List<SeriesComment> userSeriesComments = seriesCommentRepository
                .findPublicByUserId(target.getId(), PageRequest.of(0, 5));

        dto.setUltimosComentariosSeries(userSeriesComments.stream().map(c -> {
            PublicProfileDto.ComentarioSerieDto cd = new PublicProfileDto.ComentarioSerieDto();
            cd.setCommentId(c.getId());
            cd.setSeriesId(c.getSeriesId());
            cd.setSpoiler(c.isSpoiler());
            cd.setFechaRelativa(formatRelativa(c.getCreatedAt()));

            String contenido = c.isSpoiler() ? "— Comentario con spoiler —" : c.getContent();
            cd.setContenido(contenido);

            Optional<com.example.demo.domain.series.Series> serie = seriesRepository.findByTmdbId(c.getSeriesId());
            serie.ifPresent(s -> {
                cd.setSeriesTitle(s.getTitle());
                cd.setPosterPath(s.getPosterPath());
            });

            cd.setBancoCount((int) seriesCommentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.BANCO));
            cd.setMerecePuntoCount((int) seriesCommentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.MERECE_PUNTO));
            cd.setReplyCount((int) seriesCommentReplyRepository.countVisibleByCommentId(c.getId()));

            return cd;
        }).toList());

        // ── Últimas recomendadas, agrupadas por película (máx 8 pósters
        // distintos) — cada póster aparece una sola vez con la cantidad
        // real de veces que se recomendó, no un póster por destinatario.
        dto.setUltimasRecomendadas(movieRecommendationRepository
                .findRecomendadasAgrupadasBySenderId(target.getId())
                .stream().limit(8).map(row -> {
                    PublicProfileDto.RecomendadaDto rd = new PublicProfileDto.RecomendadaDto();
                    rd.setMovieId((Long) row[0]);
                    rd.setMovieTitle((String) row[1]);
                    rd.setPosterPath((String) row[2]);
                    rd.setVeces((Long) row[3]);
                    return rd;
                }).toList());

        // ── Últimas recomendadas de series, mismo criterio agrupado ──
        dto.setUltimasRecomendadasSeries(seriesRecommendationRepository
                .findRecomendadasAgrupadasBySenderId(target.getId())
                .stream().limit(8).map(row -> {
                    PublicProfileDto.RecomendadaSerieDto rd = new PublicProfileDto.RecomendadaSerieDto();
                    rd.setSeriesId((Long) row[0]);
                    rd.setSeriesTitle((String) row[1]);
                    rd.setPosterPath((String) row[2]);
                    rd.setVeces((Long) row[3]);
                    return rd;
                }).toList());

        // ── Últimas guardadas (máx 8) ───────────────────────────
        dto.setUltimasGuardadas(watchlistRepository
                .findByUserIdOrderByCreatedAtDesc(target.getId())
                .stream().limit(8).map(w -> {
                    PublicProfileDto.GuardadaDto gd = new PublicProfileDto.GuardadaDto();
                    gd.setMovieId(w.getMovieId());
                    gd.setMovieTitle(w.getMovieTitle());
                    gd.setPosterPath(w.getMoviePosterPath());
                    return gd;
                }).toList());

        // ── Últimas guardadas de series (máx 8) ─────────────────
        dto.setUltimasGuardadasSeries(seriesWatchlistRepository
                .findByUserIdOrderByCreatedAtDesc(target.getId())
                .stream().limit(8).map(w -> {
                    PublicProfileDto.GuardadaSerieDto gd = new PublicProfileDto.GuardadaSerieDto();
                    gd.setSeriesId(w.getSeriesId());
                    gd.setSeriesTitle(w.getSeriesTitle());
                    gd.setPosterPath(w.getSeriesPosterPath());
                    return gd;
                }).toList());

        return ResponseEntity.ok(dto);
    }

    // GET /api/users/{id}/votaciones?page=0&size=8
    @GetMapping("/{id}/votaciones")
    public ResponseEntity<?> getVotaciones(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Review> todas = reviewRepository
                .findByUserIdOrderByCreatedAtDesc(id)
                .stream()
                .filter(r -> r.getReviewType() == ReviewType.MOVIE && r.getVote() != null)
                .toList();

        int total = todas.size();
        int from  = page * size;
        int to    = Math.min(from + size, total);
        boolean hayMas = to < total;

        List<PublicProfileDto.VotacionDto> lote = todas.subList(from, to)
                .stream().map(r -> {
                    PublicProfileDto.VotacionDto v = new PublicProfileDto.VotacionDto();
                    v.setMovieId(r.getTargetId());
                    v.setVoto(r.getVote().name());
                    Optional<Movie> movie = movieRepository.findByTmdbId(r.getTargetId());
                    movie.ifPresent(m -> {
                        v.setMovieTitle(m.getTitle());
                        v.setPosterPath(m.getPosterPath());
                    });
                    return v;
                }).toList();

        return ResponseEntity.ok(java.util.Map.of(
                "votaciones", lote,
                "hayMas", hayMas,
                "page", page,
                "total", total
        ));
    }

    // GET /api/users/{id}/comentarios?page=0&size=5
    @GetMapping("/{id}/comentarios")
    public ResponseEntity<?> getComentarios(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        long total = commentRepository.countByUserId(id);
        List<Comment> lote = commentRepository.findPublicByUserId(id, PageRequest.of(page, size));
        boolean hayMas = (long)(page + 1) * size < total;

        List<PublicProfileDto.ComentarioPublicoDto> dtos = lote.stream().map(c -> {
            PublicProfileDto.ComentarioPublicoDto cd = new PublicProfileDto.ComentarioPublicoDto();
            cd.setCommentId(c.getId());
            cd.setMovieId(c.getMovieId());
            cd.setSpoiler(c.isSpoiler());
            cd.setFechaRelativa(formatRelativa(c.getCreatedAt()));
            String contenido = c.isSpoiler() ? "— Comentario con spoiler —" : c.getContent();
            cd.setContenido(contenido.length() > 120 ? contenido.substring(0, 120) + "..." : contenido);
            Optional<Movie> movie = movieRepository.findByTmdbId(c.getMovieId());
            movie.ifPresent(m -> {
                cd.setMovieTitle(m.getTitle());
                cd.setPosterPath(m.getPosterPath());
            });
            cd.setBancoCount((int) commentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.BANCO));
            cd.setMerecePuntoCount((int) commentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.MERECE_PUNTO));
            cd.setReplyCount((int) commentRepository.countByUserId(c.getId()));
            return cd;
        }).toList();

        return ResponseEntity.ok(java.util.Map.of(
                "comentarios", dtos,
                "hayMas", hayMas,
                "page", page,
                "total", total
        ));
    }

    // GET /api/users/{id}/comentarios-series?page=0&size=5
    @GetMapping("/{id}/comentarios-series")
    public ResponseEntity<?> getComentariosSeries(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        long total = seriesCommentRepository.countByUserId(id);
        List<SeriesComment> lote = seriesCommentRepository.findPublicByUserId(id, PageRequest.of(page, size));
        boolean hayMas = (long)(page + 1) * size < total;

        List<PublicProfileDto.ComentarioSerieDto> dtos = lote.stream().map(c -> {
            PublicProfileDto.ComentarioSerieDto cd = new PublicProfileDto.ComentarioSerieDto();
            cd.setCommentId(c.getId());
            cd.setSeriesId(c.getSeriesId());
            cd.setSpoiler(c.isSpoiler());
            cd.setFechaRelativa(formatRelativa(c.getCreatedAt()));
            String contenido = c.isSpoiler() ? "— Comentario con spoiler —" : c.getContent();
            cd.setContenido(contenido.length() > 120 ? contenido.substring(0, 120) + "..." : contenido);
            Optional<com.example.demo.domain.series.Series> serie = seriesRepository.findByTmdbId(c.getSeriesId());
            serie.ifPresent(s -> {
                cd.setSeriesTitle(s.getTitle());
                cd.setPosterPath(s.getPosterPath());
            });
            cd.setBancoCount((int) seriesCommentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.BANCO));
            cd.setMerecePuntoCount((int) seriesCommentReactionRepository
                    .countByCommentIdAndTypeAndActiveTrue(c.getId(), ReactionType.MERECE_PUNTO));
            cd.setReplyCount((int) seriesCommentReplyRepository.countVisibleByCommentId(c.getId()));
            return cd;
        }).toList();

        return ResponseEntity.ok(java.util.Map.of(
                "comentarios", dtos,
                "hayMas", hayMas,
                "page", page,
                "total", total
        ));
    }

    // ── helpers ────────────────────────────────────────────────
    // Resuelve por tmdbId desde la base local — null si el gusto no está
    // elegido, o si por algún motivo la película/serie todavía no está
    // persistida (dato legado de antes de este cambio; se autocompleta
    // la próxima vez que el usuario la vuelva a elegir).
    private Movie resolverPelicula(Integer tmdbId) {
        if (tmdbId == null) return null;
        return movieRepository.findByTmdbId(tmdbId.longValue()).orElse(null);
    }

    private Series resolverSerie(Integer tmdbId) {
        if (tmdbId == null) return null;
        return seriesRepository.findByTmdbId(tmdbId.longValue()).orElse(null);
    }

    private String formatMiembroDesde(LocalDateTime dt) {
        if (dt == null) return "";
        String[] meses = {"enero","febrero","marzo","abril","mayo","junio",
                "julio","agosto","septiembre","octubre","noviembre","diciembre"};
        return meses[dt.getMonthValue() - 1] + " " + dt.getYear();
    }

    private String formatRelativa(LocalDateTime dt) {
        if (dt == null) return "";
        long minutos = ChronoUnit.MINUTES.between(dt, LocalDateTime.now());
        if (minutos < 60)   return "hace " + minutos + " min";
        long horas = ChronoUnit.HOURS.between(dt, LocalDateTime.now());
        if (horas < 24)     return "hace " + horas + " h";
        long dias = ChronoUnit.DAYS.between(dt, LocalDateTime.now());
        if (dias < 7)       return "hace " + dias + " días";
        long semanas = dias / 7;
        if (semanas < 4)    return "hace " + semanas + " semanas";
        long meses = ChronoUnit.MONTHS.between(dt, LocalDateTime.now());
        return "hace " + meses + " meses";
    }

    // GET /api/users/{id}/seguidores
    @GetMapping("/{id}/seguidores")
    public ResponseEntity<?> getSeguidores(@PathVariable Long id) {
        List<UserFollow> follows = followRepository.findFollowersByUserId(id)
                .stream()
                .filter(f -> "ACCEPTED".equals(f.getStatus()))
                .toList();
        List<java.util.Map<String, Object>> lista = follows.stream().map(f -> {
            java.util.Map<String, Object> u = new java.util.HashMap<>();
            u.put("id", f.getFollower().getId());
            u.put("nombre", f.getFollower().getName());
            u.put("avatarUrl", f.getFollower().getEffectiveAvatarUrl());
            u.put("nivel", f.getFollower().getLevel() != null ? f.getFollower().getLevel().getDisplayName() : "Amateur");
            return u;
        }).toList();
        return ResponseEntity.ok(lista);
    }

    // GET /api/users/{id}/seguidos
    @GetMapping("/{id}/seguidos")
    public ResponseEntity<?> getSeguidos(@PathVariable Long id) {
        List<UserFollow> follows = followRepository.findFollowingByUserId(id)
                .stream()
                .filter(f -> "ACCEPTED".equals(f.getStatus()))
                .toList();
        List<java.util.Map<String, Object>> lista = follows.stream().map(f -> {
            java.util.Map<String, Object> u = new java.util.HashMap<>();
            u.put("id", f.getFollowing().getId());
            u.put("nombre", f.getFollowing().getName());
            u.put("avatarUrl", f.getFollowing().getEffectiveAvatarUrl());
            u.put("nivel", f.getFollowing().getLevel() != null ? f.getFollowing().getLevel().getDisplayName() : "Amateur");
            return u;
        }).toList();
        return ResponseEntity.ok(lista);
    }
}