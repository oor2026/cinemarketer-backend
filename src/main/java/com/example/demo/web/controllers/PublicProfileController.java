package com.example.demo.web.controllers;

import com.example.demo.application.dtos.PublicProfileDto;
import com.example.demo.domain.comment.Comment;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.comment.CommentReactionRepository;
import com.example.demo.domain.comment.CommentReply;
import com.example.demo.domain.review.*;
import com.example.demo.domain.comment.CommentReplyRepository;
import com.example.demo.domain.comment.ReactionType;
import com.example.demo.domain.follow.UserFollow;
import com.example.demo.domain.follow.UserFollowRepository;
import com.example.demo.domain.movie.Movie;
import com.example.demo.domain.movie.MovieRepository;
import com.example.demo.domain.series.*;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.user.UserBlockRepository;
import com.example.demo.domain.comment.ReactionType;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        dto.setBannerUrl(target.getBannerUrl());
        dto.setNivel(target.getLevel() != null ? target.getLevel().name() : "AMATEUR");
        dto.setNivelEmoji(target.getLevel() != null ? target.getLevel().getEmoji() : "🟢");
        dto.setNivelDisplayName(target.getLevel() != null ? target.getLevel().getDisplayName() : "Amateur");
        dto.setMiembroDesde(formatMiembroDesde(target.getCreatedAt()));
        dto.setBioTitulo(target.getBioTitulo());
        dto.setBioTexto(target.getBioTexto());
        // Los 4 "gustos" de películas y los 4 de series se resuelven con
        // UNA query cada grupo (findByTmdbIdIn), en vez de 8 llamadas a
        // resolverPelicula()/resolverSerie() secuenciales — mismo criterio
        // batch que ya usamos para votaciones/comentarios/recomendadas.
        List<Long> peliculaGustoIds = java.util.stream.Stream.of(
                        target.getPeliculaFavoritaId(), target.getUltimaVistaCineId(),
                        target.getNoMeCansoDeVerId(), target.getNoLaBancoId())
                .filter(java.util.Objects::nonNull)
                .map(Integer::longValue)
                .toList();
        Map<Long, Movie> peliculasGustoById = peliculaGustoIds.isEmpty()
                ? Map.of()
                : movieRepository.findByTmdbIdIn(peliculaGustoIds).stream()
                .collect(Collectors.toMap(Movie::getTmdbId, m -> m));

        List<Long> serieGustoIds = java.util.stream.Stream.of(
                        target.getSerieFavoritaId(), target.getUltimaMaratonId(),
                        target.getNoMeCansoDeVerSerieId(), target.getNoLaBancoSerieId())
                .filter(java.util.Objects::nonNull)
                .map(Integer::longValue)
                .toList();
        Map<Long, Series> seriesGustoById = serieGustoIds.isEmpty()
                ? Map.of()
                : seriesRepository.findByTmdbIdIn(serieGustoIds).stream()
                .collect(Collectors.toMap(Series::getTmdbId, s -> s));

        dto.setPeliculaFavoritaId(target.getPeliculaFavoritaId());
        Movie peliculaFavorita = target.getPeliculaFavoritaId() != null
                ? peliculasGustoById.get(target.getPeliculaFavoritaId().longValue()) : null;
        if (peliculaFavorita != null) {
            dto.setPeliculaFavoritaTitulo(peliculaFavorita.getTitle());
            dto.setPeliculaFavoritaPoster(peliculaFavorita.getPosterPath());
        }

        dto.setUltimaVistaCineId(target.getUltimaVistaCineId());
        Movie ultimaVistaCine = target.getUltimaVistaCineId() != null
                ? peliculasGustoById.get(target.getUltimaVistaCineId().longValue()) : null;
        if (ultimaVistaCine != null) {
            dto.setUltimaVistaCineTitulo(ultimaVistaCine.getTitle());
            dto.setUltimaVistaCinePoster(ultimaVistaCine.getPosterPath());
        }

        dto.setNoMeCansoDeVerId(target.getNoMeCansoDeVerId());
        Movie noMeCansoDeVer = target.getNoMeCansoDeVerId() != null
                ? peliculasGustoById.get(target.getNoMeCansoDeVerId().longValue()) : null;
        if (noMeCansoDeVer != null) {
            dto.setNoMeCansoDeVerTitulo(noMeCansoDeVer.getTitle());
            dto.setNoMeCansoDeVerPoster(noMeCansoDeVer.getPosterPath());
        }

        dto.setNoLaBancoId(target.getNoLaBancoId());
        Movie noLaBanco = target.getNoLaBancoId() != null
                ? peliculasGustoById.get(target.getNoLaBancoId().longValue()) : null;
        if (noLaBanco != null) {
            dto.setNoLaBancoTitulo(noLaBanco.getTitle());
            dto.setNoLaBancoPoster(noLaBanco.getPosterPath());
        }

        dto.setSerieFavoritaId(target.getSerieFavoritaId());
        Series serieFavorita = target.getSerieFavoritaId() != null
                ? seriesGustoById.get(target.getSerieFavoritaId().longValue()) : null;
        if (serieFavorita != null) {
            dto.setSerieFavoritaTitulo(serieFavorita.getTitle());
            dto.setSerieFavoritaPoster(serieFavorita.getPosterPath());
        }

        dto.setUltimaMaratonId(target.getUltimaMaratonId());
        Series ultimaMaraton = target.getUltimaMaratonId() != null
                ? seriesGustoById.get(target.getUltimaMaratonId().longValue()) : null;
        if (ultimaMaraton != null) {
            dto.setUltimaMaratonTitulo(ultimaMaraton.getTitle());
            dto.setUltimaMaratonPoster(ultimaMaraton.getPosterPath());
        }

        dto.setNoMeCansoDeVerSerieId(target.getNoMeCansoDeVerSerieId());
        Series noMeCansoDeVerSerie = target.getNoMeCansoDeVerSerieId() != null
                ? seriesGustoById.get(target.getNoMeCansoDeVerSerieId().longValue()) : null;
        if (noMeCansoDeVerSerie != null) {
            dto.setNoMeCansoDeVerSerieTitulo(noMeCansoDeVerSerie.getTitle());
            dto.setNoMeCansoDeVerSeriePoster(noMeCansoDeVerSerie.getPosterPath());
        }

        dto.setNoLaBancoSerieId(target.getNoLaBancoSerieId());
        Series noLaBancoSerie = target.getNoLaBancoSerieId() != null
                ? seriesGustoById.get(target.getNoLaBancoSerieId().longValue()) : null;
        if (noLaBancoSerie != null) {
            dto.setNoLaBancoSerieTitulo(noLaBancoSerie.getTitle());
            dto.setNoLaBancoSeriePoster(noLaBancoSerie.getPosterPath());
        }
        // adnCinefilo / adnCinefiloSeries se movieron a un endpoint aparte
        // (GET /{id}/adn-cinefilo-completo), pedido en paralelo desde el
        // frontend — así no bloquean el primer pintado de Mi Sala.

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
        dto.setTotalComentarios(commentRepository.countPublicByUserId(target.getId())
                + seriesCommentRepository.countPublicByUserId(target.getId()));
        dto.setTotalRecomendadas(movieRecommendationRepository.countBySenderId(target.getId())
                + seriesRecommendationRepository.countBySenderId(target.getId()));
        dto.setTotalGuardadas(watchlistRepository.countByUserId(target.getId())
                + seriesWatchlistRepository.countByUserId(target.getId()));

        // Conteos individuales por tipo — para los títulos "(N)" de cada mazo
        dto.setTotalVotacionesPeliculas(reviewRepository.countByUserId(target.getId()));
        dto.setTotalVotacionesSeries(seriesReviewRepository.countByUserId(target.getId()));
        dto.setTotalComentariosPeliculas(commentRepository.countPublicByUserId(target.getId()));
        dto.setTotalComentariosSeries(seriesCommentRepository.countPublicByUserId(target.getId()));
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
                .findVotacionesRecientesByUserId(target.getId(), PageRequest.of(0, 6));

        // Una sola query trayendo todas las películas de este lote, en
        // vez de un findByTmdbId() por cada una de las 6 votaciones.
        Map<Long, Movie> peliculasVotacionesById = movieRepository
                .findByTmdbIdIn(reviews.stream().map(Review::getTargetId).toList())
                .stream().collect(Collectors.toMap(Movie::getTmdbId, m -> m));

        dto.setUltimasVotaciones(reviews.stream().map(r -> {
            PublicProfileDto.VotacionDto v = new PublicProfileDto.VotacionDto();
            v.setMovieId(r.getTargetId());
            v.setVoto(r.getVote().name());
            Movie m = peliculasVotacionesById.get(r.getTargetId());
            if (m != null) {
                v.setMovieTitle(m.getTitle());
                v.setPosterPath(m.getPosterPath());
            }
            return v;
        }).toList());

        // ── Últimas votaciones de series (máx 6) ───────────────
        List<com.example.demo.domain.series.SeriesReview> seriesReviews = seriesReviewRepository
                .findByUserIdAndVoteIsNotNullOrderByCreatedAtDesc(target.getId(), PageRequest.of(0, 6))
                .getContent();

        Map<Long, com.example.demo.domain.series.Series> seriesVotacionesById = seriesRepository
                .findByTmdbIdIn(seriesReviews.stream().map(com.example.demo.domain.series.SeriesReview::getSeriesId).toList())
                .stream().collect(Collectors.toMap(com.example.demo.domain.series.Series::getTmdbId, s -> s));

        dto.setUltimasVotacionesSeries(seriesReviews.stream().map(r -> {
            PublicProfileDto.VotacionSerieDto v = new PublicProfileDto.VotacionSerieDto();
            v.setSeriesId(r.getSeriesId());
            v.setVoto(r.getVote().name());
            com.example.demo.domain.series.Series s = seriesVotacionesById.get(r.getSeriesId());
            if (s != null) {
                v.setSeriesTitle(s.getTitle());
                v.setPosterPath(s.getPosterPath());
            }
            return v;
        }).toList());

        // Usamos el query correcto por userId
        List<Comment> userComments = commentRepository
                .findPublicByUserId(target.getId(), PageRequest.of(0, 5));

        Map<Long, Movie> peliculasComentariosById = movieRepository
                .findByTmdbIdIn(userComments.stream().map(Comment::getMovieId).toList())
                .stream().collect(Collectors.toMap(Movie::getTmdbId, m -> m));

        List<Long> comentarioIds = userComments.stream().map(Comment::getId).toList();

        // reaccionesPorComentario: commentId -> { "BANCO" -> N, "MERECE_PUNTO" -> N }
        Map<Long, Map<ReactionType, Long>> reaccionesPorComentario = new java.util.HashMap<>();
        for (Object[] fila : commentReactionRepository.countByCommentIdsGroupedByType(comentarioIds)) {
            Long commentId = (Long) fila[0];
            ReactionType tipo = (ReactionType) fila[1];
            Long cantidad = (Long) fila[2];
            reaccionesPorComentario.computeIfAbsent(commentId, k -> new java.util.HashMap<>()).put(tipo, cantidad);
        }

        Map<Long, Long> respuestasPorComentario = new java.util.HashMap<>();
        for (Object[] fila : commentReplyRepository.countVisibleByCommentIds(comentarioIds)) {
            respuestasPorComentario.put((Long) fila[0], (Long) fila[1]);
        }

        dto.setUltimosComentarios(userComments.stream().map(c -> {
            PublicProfileDto.ComentarioPublicoDto cd = new PublicProfileDto.ComentarioPublicoDto();
            cd.setCommentId(c.getId());
            cd.setMovieId(c.getMovieId());
            cd.setSpoiler(c.isSpoiler());
            cd.setFechaRelativa(formatRelativa(c.getCreatedAt()));

            String contenido = c.isSpoiler() ? "— Comentario con spoiler —" : c.getContent();
            cd.setContenido(contenido);

            Movie m = peliculasComentariosById.get(c.getMovieId());
            if (m != null) {
                cd.setMovieTitle(m.getTitle());
                cd.setPosterPath(m.getPosterPath());
            }

            Map<ReactionType, Long> reacciones = reaccionesPorComentario.getOrDefault(c.getId(), Map.of());
            cd.setBancoCount(reacciones.getOrDefault(ReactionType.BANCO, 0L).intValue());
            cd.setMerecePuntoCount(reacciones.getOrDefault(ReactionType.MERECE_PUNTO, 0L).intValue());
            cd.setReplyCount(respuestasPorComentario.getOrDefault(c.getId(), 0L).intValue());

            return cd;
        }).toList());

        // ── Últimos comentarios de series (máx 5) ──────────────
        List<SeriesComment> userSeriesComments = seriesCommentRepository
                .findPublicByUserId(target.getId(), PageRequest.of(0, 5));

        Map<Long, com.example.demo.domain.series.Series> seriesComentariosById = seriesRepository
                .findByTmdbIdIn(userSeriesComments.stream().map(SeriesComment::getSeriesId).toList())
                .stream().collect(Collectors.toMap(com.example.demo.domain.series.Series::getTmdbId, s -> s));

        List<Long> comentarioSerieIds = userSeriesComments.stream().map(SeriesComment::getId).toList();

        Map<Long, Map<ReactionType, Long>> reaccionesPorComentarioSerie = new java.util.HashMap<>();
        for (Object[] fila : seriesCommentReactionRepository.countByCommentIdsGroupedByType(comentarioSerieIds)) {
            Long commentId = (Long) fila[0];
            ReactionType tipo = (ReactionType) fila[1];
            Long cantidad = (Long) fila[2];
            reaccionesPorComentarioSerie.computeIfAbsent(commentId, k -> new java.util.HashMap<>()).put(tipo, cantidad);
        }

        Map<Long, Long> respuestasPorComentarioSerie = new java.util.HashMap<>();
        for (Object[] fila : seriesCommentReplyRepository.countVisibleByCommentIds(comentarioSerieIds)) {
            respuestasPorComentarioSerie.put((Long) fila[0], (Long) fila[1]);
        }

        dto.setUltimosComentariosSeries(userSeriesComments.stream().map(c -> {
            PublicProfileDto.ComentarioSerieDto cd = new PublicProfileDto.ComentarioSerieDto();
            cd.setCommentId(c.getId());
            cd.setSeriesId(c.getSeriesId());
            cd.setSpoiler(c.isSpoiler());
            cd.setFechaRelativa(formatRelativa(c.getCreatedAt()));

            String contenido = c.isSpoiler() ? "— Comentario con spoiler —" : c.getContent();
            cd.setContenido(contenido);

            com.example.demo.domain.series.Series s = seriesComentariosById.get(c.getSeriesId());
            if (s != null) {
                cd.setSeriesTitle(s.getTitle());
                cd.setPosterPath(s.getPosterPath());
            }

            Map<ReactionType, Long> reacciones = reaccionesPorComentarioSerie.getOrDefault(c.getId(), Map.of());
            cd.setBancoCount(reacciones.getOrDefault(ReactionType.BANCO, 0L).intValue());
            cd.setMerecePuntoCount(reacciones.getOrDefault(ReactionType.MERECE_PUNTO, 0L).intValue());
            cd.setReplyCount(respuestasPorComentarioSerie.getOrDefault(c.getId(), 0L).intValue());

            return cd;
        }).toList());

        // ── Últimas recomendadas, agrupadas por película (máx 8 pósters
        // distintos) — cada póster aparece una sola vez con la cantidad
        // real de veces que se recomendó, no un póster por destinatario.
        dto.setUltimasRecomendadas(movieRecommendationRepository
                .findRecomendadasAgrupadasBySenderId(target.getId(), PageRequest.of(0, 8))
                .stream().map(row -> {
                    PublicProfileDto.RecomendadaDto rd = new PublicProfileDto.RecomendadaDto();
                    rd.setMovieId((Long) row[0]);
                    rd.setMovieTitle((String) row[1]);
                    rd.setPosterPath((String) row[2]);
                    rd.setVeces((Long) row[3]);
                    return rd;
                }).toList());

        // ── Últimas recomendadas de series, mismo criterio agrupado ──
        dto.setUltimasRecomendadasSeries(seriesRecommendationRepository
                .findRecomendadasAgrupadasBySenderId(target.getId(), PageRequest.of(0, 8))
                .stream().map(row -> {
                    PublicProfileDto.RecomendadaSerieDto rd = new PublicProfileDto.RecomendadaSerieDto();
                    rd.setSeriesId((Long) row[0]);
                    rd.setSeriesTitle((String) row[1]);
                    rd.setPosterPath((String) row[2]);
                    rd.setVeces((Long) row[3]);
                    return rd;
                }).toList());

        // ── Últimas guardadas (máx 8) ───────────────────────────
        dto.setUltimasGuardadas(watchlistRepository
                .findTop8ByUserIdOrderByCreatedAtDesc(target.getId())
                .stream().map(w -> {
                    PublicProfileDto.GuardadaDto gd = new PublicProfileDto.GuardadaDto();
                    gd.setMovieId(w.getMovieId());
                    gd.setMovieTitle(w.getMovieTitle());
                    gd.setPosterPath(w.getMoviePosterPath());
                    return gd;
                }).toList());

        // ── Últimas guardadas de series (máx 8) ─────────────────
        dto.setUltimasGuardadasSeries(seriesWatchlistRepository
                .findTop8ByUserIdOrderByCreatedAtDesc(target.getId())
                .stream().map(w -> {
                    PublicProfileDto.GuardadaSerieDto gd = new PublicProfileDto.GuardadaSerieDto();
                    gd.setSeriesId(w.getSeriesId());
                    gd.setSeriesTitle(w.getSeriesTitle());
                    gd.setPosterPath(w.getSeriesPosterPath());
                    return gd;
                }).toList());

        return ResponseEntity.ok(dto);
    }

    // GET /api/users/{id}/adn-cinefilo-completo — separado de /profile para
    // no bloquear el primer pintado de Mi Sala con este cálculo. El
    // frontend lo pide en paralelo, no en cadena.
    @GetMapping("/{id}/adn-cinefilo-completo")
    public ResponseEntity<?> getAdnCinefiloCompleto(@PathVariable Long id) {
        java.util.Map<String, Object> resultado = new java.util.HashMap<>();
        resultado.put("adnCinefilo", adnCinefiloService.calcular(id));
        resultado.put("adnCinefiloSeries", adnCinefiloSeriesService.calcular(id));
        return ResponseEntity.ok(resultado);
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

    // GET /api/users/{id}/votaciones-series?page=0&size=6
    // Faltaba este endpoint — el frontend (feed-series.js) ya le pega
    // hace rato, pero nunca se creó del lado del backend. Por eso el
    // mazo de votaciones-series se quedaba repitiendo el primer lote
    // para siempre: pedía la página siguiente, recibía 404, y el
    // catch del frontend solo logueaba el error sin avisar a nadie.
    @GetMapping("/{id}/votaciones-series")
    public ResponseEntity<?> getVotacionesSeries(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<SeriesReview> todas = seriesReviewRepository
                .findByUserIdAndVoteIsNotNullOrderByCreatedAtDesc(id);

        int total = todas.size();
        int from  = page * size;
        int to    = Math.min(from + size, total);
        boolean hayMas = to < total;

        List<PublicProfileDto.VotacionSerieDto> lote = todas.subList(from, to)
                .stream().map(r -> {
                    PublicProfileDto.VotacionSerieDto v = new PublicProfileDto.VotacionSerieDto();
                    v.setSeriesId(r.getSeriesId());
                    v.setVoto(r.getVote().name());
                    Optional<com.example.demo.domain.series.Series> serie = seriesRepository.findByTmdbId(r.getSeriesId());
                    serie.ifPresent(s -> {
                        v.setSeriesTitle(s.getTitle());
                        v.setPosterPath(s.getPosterPath());
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

        long total = commentRepository.countPublicByUserId(id);
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

        long total = seriesCommentRepository.countPublicByUserId(id);
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

    // GET /api/users/{id}/adn-cinefilo/genero/{generoId}/peliculas
    // Películas que componen el ADN Cinéfilo del usuario para ESE género
    // puntual — solo LIKE (nunca DISLIKE, aunque reste en el cálculo) +
    // recomendadas, mismo criterio que ya usa el algoritmo del ADN.
    @GetMapping("/{id}/adn-cinefilo/genero/{generoId}/peliculas")
    public ResponseEntity<?> getPeliculasPorGeneroAdn(
            @PathVariable Long id,
            @PathVariable Long generoId) {

        userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Long> tmdbIdsVotadasLike = reviewRepository.findByUserIdOrderByCreatedAtDesc(id).stream()
                .filter(r -> r.getReviewType() == ReviewType.MOVIE && r.getVote() == VoteType.LIKE)
                .map(Review::getTargetId)
                .toList();

        List<Long> tmdbIdsRecomendadas = movieRecommendationRepository.findBySenderIdOrderByCreatedAtDesc(id).stream()
                .map(com.example.demo.domain.recommendation.MovieRecommendation::getMovieId)
                .toList();

        // LinkedHashSet: dedupe si una película fue votada Y recomendada,
        // conservando el orden de inserción (votos primero, después recos).
        java.util.Set<Long> tmdbIds = new java.util.LinkedHashSet<>();
        tmdbIds.addAll(tmdbIdsVotadasLike);
        tmdbIds.addAll(tmdbIdsRecomendadas);

        List<PublicProfileDto.AdnPeliculaItemDto> resultado = tmdbIds.stream()
                .map(movieRepository::findByTmdbId)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(m -> m.getGeneroPrincipal() != null && generoId.equals(m.getGeneroPrincipal().getId()))
                .map(m -> new PublicProfileDto.AdnPeliculaItemDto(m.getTmdbId(), m.getTitle(), m.getPosterPath()))
                .toList();

        return ResponseEntity.ok(resultado);
    }

    // GET /api/users/{id}/adn-cinefilo-series/genero/{generoId}/series
    // Espejo del endpoint de Películas — mismo criterio (LIKE + recomendadas).
    @GetMapping("/{id}/adn-cinefilo-series/genero/{generoId}/series")
    public ResponseEntity<?> getSeriesPorGeneroAdn(
            @PathVariable Long id,
            @PathVariable Long generoId) {

        userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Long> tmdbIdsVotadasLike = seriesReviewRepository.findByUserIdAndVoteIsNotNullOrderByCreatedAtDesc(id).stream()
                .filter(sr -> sr.getVote() == VoteType.LIKE)
                .map(com.example.demo.domain.series.SeriesReview::getSeriesId)
                .toList();

        List<Long> tmdbIdsRecomendadas = seriesRecommendationRepository.findBySenderIdOrderByCreatedAtDesc(id).stream()
                .map(com.example.demo.domain.recommendation.SeriesRecommendation::getSeriesId)
                .toList();

        java.util.Set<Long> tmdbIds = new java.util.LinkedHashSet<>();
        tmdbIds.addAll(tmdbIdsVotadasLike);
        tmdbIds.addAll(tmdbIdsRecomendadas);

        List<PublicProfileDto.AdnSerieItemDto> resultado = tmdbIds.stream()
                .map(seriesRepository::findByTmdbId)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(s -> s.getGeneroPrincipal() != null && generoId.equals(s.getGeneroPrincipal().getId()))
                .map(s -> new PublicProfileDto.AdnSerieItemDto(s.getTmdbId(), s.getTitle(), s.getPosterPath()))
                .toList();

        return ResponseEntity.ok(resultado);
    }
}