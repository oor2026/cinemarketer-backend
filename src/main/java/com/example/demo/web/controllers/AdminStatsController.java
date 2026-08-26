package com.example.demo.web.controllers;

import com.example.demo.application.dtos.*;
import com.example.demo.domain.comment.CommentReplyRepository;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.pointtransaction.PointTransactionRepository;
import com.example.demo.domain.recommendation.MovieRecommendationRepository;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.redemption.RedemptionStatus;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.review.VoteType;
import com.example.demo.domain.series.SeriesReviewRepository;
import com.example.demo.domain.series.SeriesCommentRepository;
import com.example.demo.domain.recommendation.SeriesRecommendationRepository;
import com.example.demo.domain.watchlist.SeriesWatchlistRepository;
import com.example.demo.domain.reward.RewardRepository;
import com.example.demo.domain.subscription.SubscriptionPaymentRepository;
import com.example.demo.domain.subscription.SubscriptionPlanRepository;
import com.example.demo.domain.support.SupportTicketRepository;
import com.example.demo.domain.support.TicketStatus;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.watchlist.WatchlistRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.domain.premium.PremiumRewardRepository;
import com.example.demo.domain.premium.PremiumRewardType;
import com.example.demo.domain.subscription.UserSubscriptionRepository;
import com.example.demo.domain.subscription.SubscriptionStatus;
import com.example.demo.domain.user.UserBlockRepository;
import com.example.demo.domain.user.UserReportRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/stats")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminStatsController {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final SeriesReviewRepository seriesReviewRepository;
    private final CommentRepository commentRepository;
    private final SeriesCommentRepository seriesCommentRepository;
    private final RedemptionRepository redemptionRepository;
    private final RewardRepository rewardRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final PremiumRewardRepository premiumRewardRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserReportRepository userReportRepository;
    private final MovieRecommendationRepository recommendationRepository;
    private final SeriesRecommendationRepository seriesRecommendationRepository;
    private final CommentReplyRepository commentReplyRepository;
    private final WatchlistRepository watchlistRepository;
    private final SeriesWatchlistRepository seriesWatchlistRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final com.example.demo.domain.publication.PublicationRepository publicationRepository;
    private final com.example.demo.domain.review.VotoRelampagoOmitidaRepository votoRelampagoOmitidaRepository;
    private final com.example.demo.domain.series.VotoRelampagoOmitidaSerieRepository votoRelampagoOmitidaSerieRepository;
    private final com.example.demo.domain.espiritu.EspirituSnapshotRepository espirituSnapshotRepository;
    private final com.example.demo.domain.gusto.GustoHistorialRepository gustoHistorialRepository;

    public AdminStatsController(
            UserRepository userRepository,
            ReviewRepository reviewRepository, SeriesReviewRepository seriesReviewRepository,
            CommentRepository commentRepository, SeriesCommentRepository seriesCommentRepository,
            RedemptionRepository redemptionRepository,
            RewardRepository rewardRepository,
            PointTransactionRepository pointTransactionRepository,
            SupportTicketRepository supportTicketRepository,
            PremiumRewardRepository premiumRewardRepository,
            UserSubscriptionRepository subscriptionRepository, SubscriptionPlanRepository subscriptionPlanRepository, UserBlockRepository userBlockRepository, UserReportRepository userReportRepository, MovieRecommendationRepository recommendationRepository, SeriesRecommendationRepository seriesRecommendationRepository, CommentReplyRepository commentReplyRepository, WatchlistRepository watchlistRepository, SeriesWatchlistRepository seriesWatchlistRepository, SubscriptionPaymentRepository subscriptionPaymentRepository, com.example.demo.domain.publication.PublicationRepository publicationRepository, com.example.demo.domain.review.VotoRelampagoOmitidaRepository votoRelampagoOmitidaRepository, com.example.demo.domain.series.VotoRelampagoOmitidaSerieRepository votoRelampagoOmitidaSerieRepository, com.example.demo.domain.espiritu.EspirituSnapshotRepository espirituSnapshotRepository, com.example.demo.domain.gusto.GustoHistorialRepository gustoHistorialRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.seriesReviewRepository = seriesReviewRepository;
        this.commentRepository = commentRepository;
        this.seriesCommentRepository = seriesCommentRepository;
        this.redemptionRepository = redemptionRepository;
        this.rewardRepository = rewardRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.premiumRewardRepository = premiumRewardRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.userBlockRepository = userBlockRepository;
        this.userReportRepository = userReportRepository;
        this.recommendationRepository = recommendationRepository;
        this.seriesRecommendationRepository = seriesRecommendationRepository;
        this.commentReplyRepository = commentReplyRepository;
        this.watchlistRepository = watchlistRepository;
        this.seriesWatchlistRepository = seriesWatchlistRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.publicationRepository = publicationRepository;
        this.votoRelampagoOmitidaRepository = votoRelampagoOmitidaRepository;
        this.votoRelampagoOmitidaSerieRepository = votoRelampagoOmitidaSerieRepository;
        this.espirituSnapshotRepository = espirituSnapshotRepository;
        this.gustoHistorialRepository = gustoHistorialRepository;
    }

    @GetMapping
    public ResponseEntity<StatsResponseDto> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        // Si no se proporcionan fechas, usar mes actual
        if (startDate == null) {
            startDate = LocalDate.now().withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        LocalDateTime prevStart = start.minusMonths(1);
        LocalDateTime prevEnd = prevStart.toLocalDate().minusDays(1).atTime(23, 59, 59);

        StatsResponseDto response = new StatsResponseDto();
        response.setPeriod(startDate.getMonth().toString() + " " + startDate.getYear());

        // Calcular todas las métricas
        response.setSummary(calculateSummary(start, end, prevStart, prevEnd));
        response.setUsers(calculateUserStats(start, end, prevStart, prevEnd));
        response.setVotes(calculateVoteStats(start, end, prevStart, prevEnd));
        response.setComments(calculateCommentStats(start, end, prevStart, prevEnd));
        response.setRedemptions(calculateRedemptionStats(start, end, prevStart, prevEnd));
        response.setPoints(calculatePointStats(start, end));
        response.setSupport(calculateSupportStats());
        response.setGrowth(calculateGrowthStats(start, end, prevStart, prevEnd));
        response.setNoVistas(calculateNoVistasStats(start, end, prevStart, prevEnd));
        response.setPreferencias(calculatePreferenciasStats(start, end));
// Premium stats
        PremiumStatsDto premiumStats = new PremiumStatsDto();
        premiumStats.setTotalPremiumRewards(premiumRewardRepository.count());
        premiumStats.setActivePremiumRewards(premiumRewardRepository.countByActiveTrue());
        premiumStats.setTotalSorteos(premiumRewardRepository.countByType(PremiumRewardType.SORTEO));
        premiumStats.setSorteosEjecutados(premiumRewardRepository.countByTypeAndDrawExecutedTrue(PremiumRewardType.SORTEO));
        premiumStats.setSorteosPendientes(premiumRewardRepository.countByTypeAndDrawExecutedFalse(PremiumRewardType.SORTEO));
        premiumStats.setTotalCanjeables(premiumRewardRepository.countByType(PremiumRewardType.CANJEABLE));
        premiumStats.setCanjeablesActivos(premiumRewardRepository.countByTypeAndActiveTrue(PremiumRewardType.CANJEABLE));
        response.setPremium(premiumStats);
        // Subscription stats
        SubscriptionStatsDto subscriptionStats = new SubscriptionStatsDto();
        subscriptionStats.setTotalSuscripciones(subscriptionRepository.count());
        subscriptionStats.setSuscripcionesActivas(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE));
        subscriptionStats.setSuscripcionesCanceladas(subscriptionRepository.countByStatus(SubscriptionStatus.CANCELLED));
        subscriptionStats.setSuscripcionesPendientes(subscriptionRepository.countByStatus(SubscriptionStatus.PENDING));
        subscriptionStats.setNuevasSuscripciones(subscriptionRepository.countByCreatedAtBetween(start, end));
        subscriptionStats.setUsuariosSuscriptos(subscriptionRepository.countDistinctActiveUsers());
        response.setSubscriptions(subscriptionStats);
        response.setRecommendations(calculateRecommendationStats());
        response.setWatchlist(calculateWatchlistStats());
        response.setRevenue(calculateRevenueStats(start, end));
        response.setPublications(calculatePublicationStats(start, end, prevStart, prevEnd));
        return ResponseEntity.ok(response);
    }

    private StatsSummaryDto calculateSummary(LocalDateTime start, LocalDateTime end,
                                             LocalDateTime prevStart, LocalDateTime prevEnd) {
        StatsSummaryDto summary = new StatsSummaryDto();

        // Usuarios totales
        long totalUsers = userRepository.count();

        // Ratio de aprobación — combina Películas + Series
        long totalLikes = reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end)
                + seriesReviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end);
        long totalDislikes = reviewRepository.countByVoteTypeInPeriod(VoteType.DISLIKE, start, end)
                + seriesReviewRepository.countByVoteTypeInPeriod(VoteType.DISLIKE, start, end);
        double approvalRate = totalLikes + totalDislikes > 0 ?
                (double) totalLikes / (totalLikes + totalDislikes) * 100 : 0;

        // Canjes totales
        long totalRedemptions = redemptionRepository.count();

        // Tickets abiertos
        long openTickets = supportTicketRepository.countByStatus(TicketStatus.OPEN);

        summary.setTotalUsers(totalUsers);
        summary.setApprovalRate(Math.round(approvalRate * 100) / 100.0);
        summary.setTotalRedemptions(totalRedemptions);
        summary.setOpenTicketsCount(openTickets);

        // Crecimientos (para las flechitas)
        long newUsersThisPeriod = userRepository.countByCreatedAtBetween(start, end);
        long newUsersPrevPeriod = userRepository.countByCreatedAtBetween(prevStart, prevEnd);
        summary.setUserGrowth(calculateGrowth(newUsersThisPeriod, newUsersPrevPeriod));

        long likesThisPeriod = reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end)
                + seriesReviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end);
        long likesPrevPeriod = reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, prevStart, prevEnd)
                + seriesReviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, prevStart, prevEnd);
        summary.setApprovalGrowth(calculateGrowth(likesThisPeriod, likesPrevPeriod));

        long redemptionsThisPeriod = redemptionRepository.countByRedemptionDateBetween(start, end);
        long redemptionsPrevPeriod = redemptionRepository.countByRedemptionDateBetween(prevStart, prevEnd);
        summary.setRedemptionGrowth(calculateGrowth(redemptionsThisPeriod, redemptionsPrevPeriod));

        return summary;
    }

    private UserStatsDto calculateUserStats(LocalDateTime start, LocalDateTime end,
                                            LocalDateTime prevStart, LocalDateTime prevEnd) {
        UserStatsDto stats = new UserStatsDto();

        stats.setTotalUsers(userRepository.count());
        stats.setActiveUsers(userRepository.countByActiveTrue());
        stats.setSuspendedUsers(userRepository.countBySuspendedTrue());
        stats.setVerifiedUsers(userRepository.countByEmailVerifiedTrue());
        stats.setNewUsers(userRepository.countByCreatedAtBetween(start, end));
        stats.setUsersWithPoints(userRepository.countByAvailablePointsGreaterThan(0));
        stats.setInactiveUsers(calculateInactiveUsers());
        stats.setBlockedUsers(userBlockRepository.countDistinctBlockedId());
        stats.setReportedUsers(userReportRepository.countDistinctReportedId());

        long newUsersPrevPeriod = userRepository.countByCreatedAtBetween(prevStart, prevEnd);
        stats.setGrowth(calculateGrowth(stats.getNewUsers(), newUsersPrevPeriod));
        stats.setNewUsersPrevPeriod(newUsersPrevPeriod);

        return stats;
    }

    private VoteStatsDto calculateVoteStats(LocalDateTime start, LocalDateTime end,
                                            LocalDateTime prevStart, LocalDateTime prevEnd) {
        VoteStatsSectionDto peliculas = calculateVoteStatsPeliculas(start, end);
        VoteStatsSectionDto series = calculateVoteStatsSeries(start, end);

        VoteStatsSectionDto total = new VoteStatsSectionDto();
        long totalLikes = peliculas.getTotalLikes() + series.getTotalLikes();
        long totalDislikes = peliculas.getTotalDislikes() + series.getTotalDislikes();
        long totalVotes = peliculas.getTotalVotes() + series.getTotalVotes();
        total.setTotalLikes(totalLikes);
        total.setTotalDislikes(totalDislikes);
        total.setTotalVotes(totalVotes);
        total.setApprovalRate(totalVotes > 0 ? (double) totalLikes / totalVotes * 100 : 0);

        // Crecimiento real: votos totales (Películas + Series) de este período vs. el anterior
        long votesPrevPeriod =
                reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, prevStart, prevEnd)
                        + reviewRepository.countByVoteTypeInPeriod(VoteType.DISLIKE, prevStart, prevEnd)
                        + seriesReviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, prevStart, prevEnd)
                        + seriesReviewRepository.countByVoteTypeInPeriod(VoteType.DISLIKE, prevStart, prevEnd);
        total.setGrowth(calculateGrowth(totalVotes, votesPrevPeriod));

        VoteStatsDto stats = new VoteStatsDto();
        stats.setTotal(total);
        stats.setPeliculas(peliculas);
        stats.setSeries(series);
        stats.setPctPeliculas(totalVotes > 0 ? (double) peliculas.getTotalVotes() / totalVotes * 100 : 0);
        stats.setPctSeries(totalVotes > 0 ? (double) series.getTotalVotes() / totalVotes * 100 : 0);
        return stats;
    }

    private VoteStatsSectionDto calculateVoteStatsPeliculas(LocalDateTime start, LocalDateTime end) {
        VoteStatsSectionDto stats = new VoteStatsSectionDto();

        long likes = reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end);
        long dislikes = reviewRepository.countByVoteTypeInPeriod(VoteType.DISLIKE, start, end);
        long total = likes + dislikes;

        stats.setTotalLikes(likes);
        stats.setTotalDislikes(dislikes);
        stats.setTotalVotes(total);
        stats.setApprovalRate(total > 0 ? (double) likes / total * 100 : 0);

        stats.setTopContent(sanitizeMapList(
                reviewRepository.findTopMoviesByVotes(start, end, PageRequest.of(0, 5))
        ));
        stats.setTopUsers(sanitizeMapList(
                reviewRepository.findTopUsersByVotes(start, end, PageRequest.of(0, 5))
        ));

        Map<String, Long> dailyTrend = new LinkedHashMap<>();
        for (Object[] row : reviewRepository.getDailyVoteCount(start, end)) {
            if (row[0] != null) {
                dailyTrend.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        stats.setDailyTrend(dailyTrend);

        return stats;
    }

    private VoteStatsSectionDto calculateVoteStatsSeries(LocalDateTime start, LocalDateTime end) {
        VoteStatsSectionDto stats = new VoteStatsSectionDto();

        long likes = seriesReviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end);
        long dislikes = seriesReviewRepository.countByVoteTypeInPeriod(VoteType.DISLIKE, start, end);
        long total = likes + dislikes;

        stats.setTotalLikes(likes);
        stats.setTotalDislikes(dislikes);
        stats.setTotalVotes(total);
        stats.setApprovalRate(total > 0 ? (double) likes / total * 100 : 0);

        stats.setTopContent(sanitizeMapList(
                seriesReviewRepository.findTopSeriesByVotes(start, end, PageRequest.of(0, 5))
        ));
        stats.setTopUsers(sanitizeMapList(
                seriesReviewRepository.findTopUsersByVotes(start, end, PageRequest.of(0, 5))
        ));

        Map<String, Long> dailyTrend = new LinkedHashMap<>();
        for (Object[] row : seriesReviewRepository.getDailyVoteCount(start, end)) {
            if (row[0] != null) {
                dailyTrend.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        stats.setDailyTrend(dailyTrend);

        return stats;
    }

    private NoVistasStatsDto calculateNoVistasStats(LocalDateTime start, LocalDateTime end,
                                                    LocalDateTime prevStart, LocalDateTime prevEnd) {
        NoVistasStatsSectionDto peliculas = calculateNoVistasPeliculas(start, end);
        NoVistasStatsSectionDto series = calculateNoVistasSeries(start, end);

        long totalOmitidas = peliculas.getTotalOmitidas() + series.getTotalOmitidas();

        NoVistasStatsSectionDto total = new NoVistasStatsSectionDto();
        total.setTotalOmitidas(totalOmitidas);

        long omitidasPrevPeriod = votoRelampagoOmitidaRepository.countVigentesInPeriod(prevStart, prevEnd)
                + votoRelampagoOmitidaSerieRepository.countVigentesInPeriod(prevStart, prevEnd);
        total.setGrowth(calculateGrowth(totalOmitidas, omitidasPrevPeriod));

        NoVistasStatsDto stats = new NoVistasStatsDto();
        stats.setTotal(total);
        stats.setPeliculas(peliculas);
        stats.setSeries(series);
        stats.setPctPeliculas(totalOmitidas > 0 ? (double) peliculas.getTotalOmitidas() / totalOmitidas * 100 : 0);
        stats.setPctSeries(totalOmitidas > 0 ? (double) series.getTotalOmitidas() / totalOmitidas * 100 : 0);
        return stats;
    }

    private PreferenciasStatsDto calculatePreferenciasStats(LocalDateTime start, LocalDateTime end) {
        PreferenciasStatsDto stats = new PreferenciasStatsDto();
        stats.setTotal(buildSeccionPreferencias(null, start, end));
        stats.setPeliculas(buildSeccionPreferencias("PELICULA", start, end));
        stats.setSeries(buildSeccionPreferencias("SERIE", start, end));
        return stats;
    }

    private PreferenciasStatsSectionDto buildSeccionPreferencias(String tipo, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> distribucion = (tipo == null)
                ? espirituSnapshotRepository.findDistribucionEspiritu(start, end)
                : espirituSnapshotRepository.findDistribucionEspirituPorTipo(tipo, start, end);
        distribucion = calcularPorcentajes(distribucion);

        List<Map<String, Object>> favoritas = (tipo == null)
                ? gustoHistorialRepository.findTopGustoTotal("FAVORITA", start, end, PageRequest.of(0, 25))
                : gustoHistorialRepository.findTopGustoPorTipo(tipo, "FAVORITA", start, end, PageRequest.of(0, 25));

        List<Map<String, Object>> noMeCanso = (tipo == null)
                ? gustoHistorialRepository.findTopGustoTotal("NO_ME_CANSO", start, end, PageRequest.of(0, 25))
                : gustoHistorialRepository.findTopGustoPorTipo(tipo, "NO_ME_CANSO", start, end, PageRequest.of(0, 25));

        List<Map<String, Object>> noLaBanco = (tipo == null)
                ? gustoHistorialRepository.findTopGustoTotal("NO_LA_BANCO", start, end, PageRequest.of(0, 25))
                : gustoHistorialRepository.findTopGustoPorTipo(tipo, "NO_LA_BANCO", start, end, PageRequest.of(0, 25));

        return new PreferenciasStatsSectionDto(distribucion, favoritas, noMeCanso, noLaBanco);
    }

    // El COUNT sale directo de SQL, pero el % lo calculamos acá — más
    // simple que meterlo en la query nativa, y evita repetir el total
    // dos veces por fila.
    private List<Map<String, Object>> calcularPorcentajes(List<Map<String, Object>> filas) {
        long total = filas.stream().mapToLong(f -> ((Number) f.get("total")).longValue()).sum();
        // Los Map<String,Object> que devuelve JPQL con alias pueden venir
        // inmutables — se copian a un HashMap propio antes de mutarlos,
        // así fila.put(...) nunca revienta con UnsupportedOperationException.
        List<Map<String, Object>> resultado = new java.util.ArrayList<>();
        for (Map<String, Object> filaOriginal : filas) {
            Map<String, Object> fila = new java.util.HashMap<>(filaOriginal);
            if (total > 0) {
                long cantidad = ((Number) fila.get("total")).longValue();
                fila.put("porcentaje", Math.round(cantidad * 1000.0 / total) / 10.0);
            }
            resultado.add(fila);
        }
        return resultado;
    }

    private NoVistasStatsSectionDto calculateNoVistasPeliculas(LocalDateTime start, LocalDateTime end) {
        NoVistasStatsSectionDto stats = new NoVistasStatsSectionDto();
        stats.setTotalOmitidas(votoRelampagoOmitidaRepository.countVigentesInPeriod(start, end));

        stats.setTopOmitidas(sanitizeMapList(
                votoRelampagoOmitidaRepository.findTopOmitidasVigentes(PageRequest.of(0, 10))
        ));

        Map<String, Long> dailyTrend = new LinkedHashMap<>();
        for (Object[] row : votoRelampagoOmitidaRepository.getDailyOmitidasCount(start, end)) {
            if (row[0] != null) {
                dailyTrend.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        stats.setDailyTrend(dailyTrend);

        return stats;
    }

    private NoVistasStatsSectionDto calculateNoVistasSeries(LocalDateTime start, LocalDateTime end) {
        NoVistasStatsSectionDto stats = new NoVistasStatsSectionDto();
        stats.setTotalOmitidas(votoRelampagoOmitidaSerieRepository.countVigentesInPeriod(start, end));

        stats.setTopOmitidas(sanitizeMapList(
                votoRelampagoOmitidaSerieRepository.findTopOmitidasVigentes(PageRequest.of(0, 10))
        ));

        Map<String, Long> dailyTrend = new LinkedHashMap<>();
        for (Object[] row : votoRelampagoOmitidaSerieRepository.getDailyOmitidasCount(start, end)) {
            if (row[0] != null) {
                dailyTrend.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        stats.setDailyTrend(dailyTrend);

        return stats;
    }

    private CommentStatsDto calculateCommentStats(LocalDateTime start, LocalDateTime end,
                                                  LocalDateTime prevStart, LocalDateTime prevEnd) {
        CommentStatsSectionDto peliculas = calculateCommentStatsPeliculas(start, end);
        CommentStatsSectionDto series = calculateCommentStatsSeries(start, end);

        long totalComments = peliculas.getTotalComments() + series.getTotalComments();
        long days = java.time.Duration.between(start, end).toDays();

        CommentStatsSectionDto total = new CommentStatsSectionDto();
        total.setTotalComments(totalComments);
        total.setCommentsPerDay(days > 0 ? (double) totalComments / days : totalComments);

        long totalPrevPeriod = commentRepository.countByCreatedAtBetween(prevStart, prevEnd)
                + seriesCommentRepository.countByCreatedAtBetween(prevStart, prevEnd);
        total.setGrowth(calculateGrowth(totalComments, totalPrevPeriod));

        CommentStatsDto stats = new CommentStatsDto();
        stats.setTotal(total);
        stats.setPeliculas(peliculas);
        stats.setSeries(series);
        stats.setPctPeliculas(totalComments > 0 ? (double) peliculas.getTotalComments() / totalComments * 100 : 0);
        stats.setPctSeries(totalComments > 0 ? (double) series.getTotalComments() / totalComments * 100 : 0);

        // Se mantienen globales — CommentReply no distingue Películas/Series
        long totalGifsComentarios = commentRepository.countByHasGifTrue() + seriesCommentRepository.countByHasGifTrue();
        long totalGifsRespuestas  = commentReplyRepository.countByHasGifTrue();
        long totalRespuestas      = commentReplyRepository.count();
        long totalComentariosHistorico = commentRepository.count() + seriesCommentRepository.count();

        stats.setTotalReplies(totalRespuestas);
        stats.setGifsEnComentarios(totalGifsComentarios);
        stats.setGifsEnRespuestas(totalGifsRespuestas);
        stats.setTasaGifComentarios(totalComentariosHistorico > 0 ?
                (double) totalGifsComentarios / totalComentariosHistorico * 100 : 0);
        stats.setTasaGifRespuestas(totalRespuestas > 0 ?
                (double) totalGifsRespuestas / totalRespuestas * 100 : 0);

        return stats;
    }

    private CommentStatsSectionDto calculateCommentStatsPeliculas(LocalDateTime start, LocalDateTime end) {
        CommentStatsSectionDto stats = new CommentStatsSectionDto();
        long totalComments = commentRepository.countByCreatedAtBetween(start, end);
        long days = java.time.Duration.between(start, end).toDays();

        stats.setTotalComments(totalComments);
        stats.setCommentsPerDay(days > 0 ? (double) totalComments / days : totalComments);
        stats.setTopContent(sanitizeMapList(
                commentRepository.findTopMoviesByComments(start, end, PageRequest.of(0, 5))
        ));
        stats.setTopUsers(sanitizeMapList(
                commentRepository.findTopUsersByComments(start, end, PageRequest.of(0, 5))
        ));
        return stats;
    }

    private CommentStatsSectionDto calculateCommentStatsSeries(LocalDateTime start, LocalDateTime end) {
        CommentStatsSectionDto stats = new CommentStatsSectionDto();
        long totalComments = seriesCommentRepository.countByCreatedAtBetween(start, end);
        long days = java.time.Duration.between(start, end).toDays();

        stats.setTotalComments(totalComments);
        stats.setCommentsPerDay(days > 0 ? (double) totalComments / days : totalComments);
        stats.setTopContent(sanitizeMapList(
                seriesCommentRepository.findTopSeriesByComments(start, end, PageRequest.of(0, 5))
        ));
        stats.setTopUsers(sanitizeMapList(
                seriesCommentRepository.findTopUsersByComments(start, end, PageRequest.of(0, 5))
        ));
        return stats;
    }

    private RedemptionStatsDto calculateRedemptionStats(LocalDateTime start, LocalDateTime end,
                                                        LocalDateTime prevStart, LocalDateTime prevEnd) {
        RedemptionStatsDto stats = new RedemptionStatsDto();

        stats.setTotalRewards(rewardRepository.countByDeletedFalse());
        stats.setActiveRewards(rewardRepository.countByActiveTrueAndDeletedFalse());
        stats.setExhaustedRewards(rewardRepository.countByStockZero());

        stats.setTotalRedemptions(redemptionRepository.countByRedemptionDateBetween(start, end));
        stats.setPendingRedemptions(redemptionRepository.countByStatusInPeriod(RedemptionStatus.PENDING, start, end));
        stats.setCompletedRedemptions(redemptionRepository.countByStatusInPeriod(RedemptionStatus.COMPLETED, start, end));
        stats.setTotalPointsSpent(redemptionRepository.sumPointsSpentInPeriod(start, end));

        long totalUsers = userRepository.count();
        stats.setRedemptionRate(totalUsers > 0 ?
                (double) stats.getTotalRedemptions() / totalUsers * 100 : 0);

        long redemptionsPrevPeriod = redemptionRepository.countByRedemptionDateBetween(prevStart, prevEnd);
        stats.setGrowth(calculateGrowth(stats.getTotalRedemptions(), redemptionsPrevPeriod));

        // Top premios más canjeados - SANITIZADO
        stats.setTopRewards(sanitizeMapList(
                redemptionRepository.findTopRewardsByRedemptions(start, end, PageRequest.of(0, 5))
        ));

        return stats;
    }

    private PointStatsDto calculatePointStats(LocalDateTime start, LocalDateTime end) {
        PointStatsDto stats = new PointStatsDto();

        stats.setTotalEarned(pointTransactionRepository.sumEarnedInPeriod(start, end));
        stats.setTotalSpent(pointTransactionRepository.sumSpentInPeriod(start, end));

        long totalUsers = userRepository.count();
        stats.setAveragePerUser(totalUsers > 0 ?
                (double) stats.getTotalEarned() / totalUsers : 0);

        // Acciones más puntuadas - SANITIZADO
        stats.setTopActions(sanitizeMapList(
                pointTransactionRepository.findTopActionsInPeriod(start, end, PageRequest.of(0, 5))
        ));

        stats.setDistribucionPorAccion(sanitizeMapList(
                pointTransactionRepository.findPointsDistributionByAction(start, end)
        ));

        return stats;
    }

    private SupportStatsDto calculateSupportStats() {
        SupportStatsDto stats = new SupportStatsDto();

        stats.setOpenTickets(supportTicketRepository.countByStatus(TicketStatus.OPEN));
        stats.setClosedTickets(supportTicketRepository.countByStatus(TicketStatus.CLOSED));
        stats.setAvgResponseTimeHours(supportTicketRepository.calculateAvgResponseTime());

        // Usuarios con más tickets - SANITIZADO
        stats.setTopUsers(sanitizeMapList(
                supportTicketRepository.findTopUsersByTickets(PageRequest.of(0, 5))
        ));

        return stats;
    }

    private GrowthStatsDto calculateGrowthStats(LocalDateTime start, LocalDateTime end,
                                                LocalDateTime prevStart, LocalDateTime prevEnd) {
        GrowthStatsDto stats = new GrowthStatsDto();

        // Crecimientos básicos
        long newUsers = userRepository.countByCreatedAtBetween(start, end);
        long newUsersPrev = userRepository.countByCreatedAtBetween(prevStart, prevEnd);
        stats.setUserGrowth(calculateGrowth(newUsers, newUsersPrev));

        long newVotes = reviewRepository.countByCreatedAtBetween(start, end);
        long newVotesPrev = reviewRepository.countByCreatedAtBetween(prevStart, prevEnd);
        stats.setVoteGrowth(calculateGrowth(newVotes, newVotesPrev));

        long newRedemptions = redemptionRepository.countByRedemptionDateBetween(start, end);
        long newRedemptionsPrev = redemptionRepository.countByRedemptionDateBetween(prevStart, prevEnd);
        stats.setRedemptionGrowth(calculateGrowth(newRedemptions, newRedemptionsPrev));

        // Tasa de abandono
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        long inactiveUsers = userRepository.countInactiveSince(threeMonthsAgo);
        long totalUsers = userRepository.count();
        stats.setChurnRate(totalUsers > 0 ? (double) inactiveUsers / totalUsers * 100 : 0);

        // Embudo de conversión
        long registeredUsers = userRepository.count();
        long usersWhoVoted = reviewRepository.countDistinctUsers();
        long usersWhoCommented = commentRepository.countDistinctUsers();
        long usersWhoRedeemed = redemptionRepository.countDistinctUsers();
        long usersWithMultipleRedemptions = redemptionRepository.countUsersWithMultipleRedemptions();

        stats.setRegistrationToVoteRate(registeredUsers > 0 ?
                (double) usersWhoVoted / registeredUsers * 100 : 0);
        stats.setVoteToCommentRate(usersWhoVoted > 0 ?
                (double) usersWhoCommented / usersWhoVoted * 100 : 0);
        stats.setVoteToRedemptionRate(usersWhoVoted > 0 ?
                (double) usersWhoRedeemed / usersWhoVoted * 100 : 0);
        stats.setRedemptionToSecondRate(usersWhoRedeemed > 0 ?
                (double) usersWithMultipleRedemptions / usersWhoRedeemed * 100 : 0);

        // Distribución por día de semana - CORREGIDO con validación null
        Map<String, Long> weekdayDist = new LinkedHashMap<>();
        List<Object[]> weekdayVotes = reviewRepository.getVoteDistributionByWeekday(start, end);
        String[] weekdays = {"Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};
        for (Object[] row : weekdayVotes) {
            if (row[0] != null) {
                int dayIndex = ((Number) row[0]).intValue() - 1;
                if (dayIndex >= 0 && dayIndex < weekdays.length) {
                    weekdayDist.put(weekdays[dayIndex], ((Number) row[1]).longValue());
                } else {
                    weekdayDist.put("Día " + dayIndex, ((Number) row[1]).longValue());
                }
            } else {

            }
        }
        stats.setWeekdayDistribution(weekdayDist);

        // Distribución por hora - CORREGIDO con validación null
        Map<String, Long> hourDist = new LinkedHashMap<>();
        List<Object[]> hourVotes = reviewRepository.getVoteDistributionByHour(start, end);
        for (Object[] row : hourVotes) {
            if (row[0] != null) {
                hourDist.put(row[0].toString() + ":00", ((Number) row[1]).longValue());
            } else {

            }
        }
        stats.setHourDistribution(hourDist);

        return stats;
    }

    private RecommendationStatsDto calculateRecommendationStats() {
        RecommendationStatsSectionDto peliculas = calculateRecommendationStatsPeliculas();
        RecommendationStatsSectionDto series = calculateRecommendationStatsSeries();

        RecommendationStatsSectionDto total = new RecommendationStatsSectionDto();
        long totalEnviadas = peliculas.getTotalEnviadas() + series.getTotalEnviadas();
        long totalVistas = peliculas.getTotalVistas() + series.getTotalVistas();
        long totalCalificadas = peliculas.getTotalCalificadas() + series.getTotalCalificadas();
        long totalConContexto = peliculas.getTotalConContexto() + series.getTotalConContexto();
        total.setTotalEnviadas(totalEnviadas);
        total.setTotalVistas(totalVistas);
        total.setTasaVisualizacion(totalEnviadas > 0 ? (double) totalVistas / totalEnviadas * 100 : 0);
        total.setTotalCalificadas(totalCalificadas);
        total.setTasaCalificacion(totalVistas > 0 ? (double) totalCalificadas / totalVistas * 100 : 0);
        total.setTotalConContexto(totalConContexto);
        total.setTasaContexto(totalEnviadas > 0 ? (double) totalConContexto / totalEnviadas * 100 : 0);

        RecommendationStatsDto stats = new RecommendationStatsDto();
        stats.setTotal(total);
        stats.setPeliculas(peliculas);
        stats.setSeries(series);
        stats.setPctPeliculas(totalEnviadas > 0 ? (double) peliculas.getTotalEnviadas() / totalEnviadas * 100 : 0);
        stats.setPctSeries(totalEnviadas > 0 ? (double) series.getTotalEnviadas() / totalEnviadas * 100 : 0);
        return stats;
    }

    private RecommendationStatsSectionDto calculateRecommendationStatsPeliculas() {
        RecommendationStatsSectionDto stats = new RecommendationStatsSectionDto();

        long total = recommendationRepository.count();
        long vistas = recommendationRepository.countBySeenAtIsNotNull();
        long calificadas = recommendationRepository.countByRatingIsNotNull();
        long conContexto = recommendationRepository.countByContextTypeIsNotNull();

        stats.setTotalEnviadas(total);
        stats.setTotalVistas(vistas);
        stats.setTasaVisualizacion(total > 0 ? (double) vistas / total * 100 : 0);
        stats.setTotalCalificadas(calificadas);
        stats.setTasaCalificacion(vistas > 0 ? (double) calificadas / vistas * 100 : 0);
        stats.setTotalConContexto(conContexto);
        stats.setTasaContexto(total > 0 ? (double) conContexto / total * 100 : 0);

        stats.setTopContent(recommendationRepository.findTopMoviesByRecommendations(PageRequest.of(0, 5))
                .stream().map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("titulo", row[0]);
                    m.put("total", row[1]);
                    return m;
                }).toList());

        stats.setTopContextos(recommendationRepository.findTopContextTypes(PageRequest.of(0, 5))
                .stream().map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("contexto", row[0]);
                    m.put("total", row[1]);
                    return m;
                }).toList());

        return stats;
    }

    private RecommendationStatsSectionDto calculateRecommendationStatsSeries() {
        RecommendationStatsSectionDto stats = new RecommendationStatsSectionDto();

        long total = seriesRecommendationRepository.count();
        long vistas = seriesRecommendationRepository.countBySeenAtIsNotNull();
        long calificadas = seriesRecommendationRepository.countByRatingIsNotNull();
        long conContexto = seriesRecommendationRepository.countByContextTypeIsNotNull();

        stats.setTotalEnviadas(total);
        stats.setTotalVistas(vistas);
        stats.setTasaVisualizacion(total > 0 ? (double) vistas / total * 100 : 0);
        stats.setTotalCalificadas(calificadas);
        stats.setTasaCalificacion(vistas > 0 ? (double) calificadas / vistas * 100 : 0);
        stats.setTotalConContexto(conContexto);
        stats.setTasaContexto(total > 0 ? (double) conContexto / total * 100 : 0);

        stats.setTopContent(seriesRecommendationRepository.findTopSeriesByRecommendations(PageRequest.of(0, 5))
                .stream().map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("titulo", row[0]);
                    m.put("total", row[1]);
                    return m;
                }).toList());

        stats.setTopContextos(seriesRecommendationRepository.findTopContextTypes(PageRequest.of(0, 5))
                .stream().map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("contexto", row[0]);
                    m.put("total", row[1]);
                    return m;
                }).toList());

        return stats;
    }

    private PublicationStatsDto calculatePublicationStats(LocalDateTime start, LocalDateTime end,
                                                          LocalDateTime prevStart, LocalDateTime prevEnd) {
        PublicationStatsDto stats = new PublicationStatsDto();

        long total = publicationRepository.countByCreatedAtBetween(start, end);
        long totalPrev = publicationRepository.countByCreatedAtBetween(prevStart, prevEnd);
        stats.setTotalPublicaciones(total);
        stats.setGrowth(calculateGrowth(total, totalPrev));

        long days = java.time.Duration.between(start, end).toDays();
        stats.setPromedioPorDia(days > 0 ? Math.round((double) total / days * 10) / 10.0 : total);

        long texto = publicationRepository.countTextoInPeriod(start, end);
        long imagen = publicationRepository.countImagenInPeriod(start, end);
        long video = publicationRepository.countVideoInPeriod(start, end);
        stats.setPublicacionesTexto(texto);
        stats.setPublicacionesImagen(imagen);
        stats.setPublicacionesVideo(video);
        long totalFormato = texto + imagen + video;
        stats.setPorcentajeTexto(totalFormato > 0 ? Math.round((double) texto / totalFormato * 1000) / 10.0 : 0);
        stats.setPorcentajeImagen(totalFormato > 0 ? Math.round((double) imagen / totalFormato * 1000) / 10.0 : 0);
        stats.setPorcentajeVideo(totalFormato > 0 ? Math.round((double) video / totalFormato * 1000) / 10.0 : 0);

        stats.setPublicacionesFichaTecnica(publicationRepository.countFichaTecnicaInPeriod(start, end));
        stats.setPublicacionesCountdown(publicationRepository.countCountdownInPeriod(start, end));
        stats.setPublicacionesVotacion(publicationRepository.countVotacionInPeriod(start, end));
        stats.setPublicacionesRanking(publicationRepository.countRankingInPeriod(start, end));
        stats.setPublicacionesTrivia(publicationRepository.countTriviaInPeriod(start, end));
        stats.setPublicacionesTrailer(publicationRepository.countTrailerInPeriod(start, end));

        long aprobadasAuto = publicationRepository.countAprobadasAutomaticamente(start, end);
        stats.setTasaAprobacionAutomatica(total > 0 ? Math.round((double) aprobadasAuto / total * 1000) / 10.0 : 0);
        stats.setPublicacionesEnRevision(publicationRepository.countPasaronPorRevision(start, end));
        stats.setPublicacionesOcultasSancionadas(publicationRepository.countByCreatedAtBetweenAndHiddenTrue(start, end));

        long totalBanco = publicationRepository.sumBancoInPeriod(start, end);
        long totalPuntos = publicationRepository.sumPuntoInPeriod(start, end);
        long totalComentarios = publicationRepository.sumComentariosInPeriod(start, end);
        stats.setTotalBanco(totalBanco);
        stats.setPromedioBancoPorPublicacion(total > 0 ? Math.round((double) totalBanco / total * 10) / 10.0 : 0);
        stats.setTotalPuntos(totalPuntos);
        stats.setPromedioPuntosPorPublicacion(total > 0 ? Math.round((double) totalPuntos / total * 10) / 10.0 : 0);
        stats.setTotalComentarios(totalComentarios);
        stats.setPromedioComentariosPorPublicacion(total > 0 ? Math.round((double) totalComentarios / total * 10) / 10.0 : 0);

        stats.setTopUsuarios(publicationRepository.findTopUsuariosByPublicaciones(start, end, 5).stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("nombre", row[0]);
            m.put("total", row[1]);
            return m;
        }).toList());

        stats.setTopCategorias(publicationRepository.findTopCategoriasByPublicaciones(start, end, 5).stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("categoria", row[0]);
            m.put("total", row[1]);
            return m;
        }).toList());

        return stats;
    }

    private RevenueStatsDto calculateRevenueStats(LocalDateTime start, LocalDateTime end) {
        RevenueStatsDto stats = new RevenueStatsDto();

        // Totales históricos
        stats.setIngresoTotalHistorico(
                subscriptionPaymentRepository.sumTotalApproved());
        stats.setPagosAprobadosTotal(
                subscriptionPaymentRepository.countByStatus("approved"));

        // Período seleccionado
        stats.setIngresoPeriodo(
                subscriptionPaymentRepository.sumApprovedInPeriod(start, end));
        stats.setPagosAprobadosPeriodo(
                subscriptionPaymentRepository.countByStatusAndPaidAtBetween("approved", start, end));
        stats.setPagosRechazadosPeriodo(
                subscriptionPaymentRepository.countByStatusAndPaidAtBetween("rejected", start, end));
        stats.setPagosPendientesPeriodo(
                subscriptionPaymentRepository.countByStatusAndPaidAtBetween("pending", start, end));

        // Tasa de aprobación
        long totalPeriodo = stats.getPagosAprobadosPeriodo()
                + stats.getPagosRechazadosPeriodo()
                + stats.getPagosPendientesPeriodo();
        stats.setTasaAprobacion(totalPeriodo > 0 ?
                (double) stats.getPagosAprobadosPeriodo() / totalPeriodo * 100 : 0);

        // MRR = suscripciones activas × precio del plan
        long activas = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
        java.math.BigDecimal precio = subscriptionPlanRepository
                .findFirstByActiveTrue()
                .map(p -> p.getPrice())
                .orElse(java.math.BigDecimal.ZERO);
        stats.setMrr(precio.multiply(java.math.BigDecimal.valueOf(activas)));

        // Tendencia mensual (últimos 12 meses)
        List<Object[]> mensual = subscriptionPaymentRepository.findMonthlyRevenue();
        stats.setTendenciaMensual(mensual.stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("mes", row[0]);
            m.put("total", row[1]);
            return m;
        }).toList());

        return stats;
    }

    private WatchlistStatsDto calculateWatchlistStats() {
        WatchlistStatsSectionDto peliculas = calculateWatchlistStatsPeliculas();
        WatchlistStatsSectionDto series = calculateWatchlistStatsSeries();

        WatchlistStatsSectionDto total = new WatchlistStatsSectionDto();
        long totalGuardadas = peliculas.getTotalGuardadas() + series.getTotalGuardadas();
        long usuariosConLista = peliculas.getUsuariosConLista() + series.getUsuariosConLista(); // aproximado: puede sobrecontar usuarios que tienen ambas listas
        total.setTotalGuardadas(totalGuardadas);
        total.setUsuariosConLista(usuariosConLista);
        total.setPromedioPorUsuario(usuariosConLista > 0 ?
                Math.round((double) totalGuardadas / usuariosConLista * 10) / 10.0 : 0);

        // "Total" mezcla motivos de Películas + Series en una sola bolsa
        // — mismo criterio que ya aplicamos con Espíritu en "Preferencias".
        Map<String, Long> motivosUnificados = new java.util.LinkedHashMap<>();
        for (var m : peliculas.getMotivos()) motivosUnificados.merge((String) m.get("motivo"), ((Number) m.get("total")).longValue(), Long::sum);
        for (var m : series.getMotivos()) motivosUnificados.merge((String) m.get("motivo"), ((Number) m.get("total")).longValue(), Long::sum);
        List<Map<String, Object>> motivosTotal = motivosUnificados.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("motivo", e.getKey()); m.put("total", e.getValue()); return m; })
                .toList();
        total.setMotivos(calcularPorcentajes(motivosTotal));

        long conMotivoTotal = peliculas.getMotivos().stream().mapToLong(m -> ((Number) m.get("total")).longValue()).sum()
                + series.getMotivos().stream().mapToLong(m -> ((Number) m.get("total")).longValue()).sum();
        total.setPctConMotivo(totalGuardadas > 0 ? Math.round(conMotivoTotal * 1000.0 / totalGuardadas) / 10.0 : 0);

        WatchlistStatsDto stats = new WatchlistStatsDto();
        stats.setTotal(total);
        stats.setPeliculas(peliculas);
        stats.setSeries(series);
        stats.setPctPeliculas(totalGuardadas > 0 ? (double) peliculas.getTotalGuardadas() / totalGuardadas * 100 : 0);
        stats.setPctSeries(totalGuardadas > 0 ? (double) series.getTotalGuardadas() / totalGuardadas * 100 : 0);
        return stats;
    }

    private WatchlistStatsSectionDto calculateWatchlistStatsPeliculas() {
        WatchlistStatsSectionDto stats = new WatchlistStatsSectionDto();

        long total = watchlistRepository.count();
        long usuariosConLista = watchlistRepository.countDistinctUsers();

        stats.setTotalGuardadas(total);
        stats.setUsuariosConLista(usuariosConLista);
        stats.setPromedioPorUsuario(usuariosConLista > 0 ?
                Math.round((double) total / usuariosConLista * 10) / 10.0 : 0);

        stats.setTopContent(watchlistRepository.findTopMovies(PageRequest.of(0, 10))
                .stream().map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("titulo", row[0]);
                    m.put("total", row[1]);
                    return m;
                }).toList());

        stats.setGeneros(calcularGenerosConPorcentaje(watchlistRepository.findAllMovieGenres()));

        long conMotivo = watchlistRepository.countByMotivoIsNotNull();
        stats.setPctConMotivo(total > 0 ? Math.round(conMotivo * 1000.0 / total) / 10.0 : 0);
        stats.setMotivos(calcularPorcentajes(watchlistRepository.findDistribucionMotivos()));

        return stats;
    }

    private WatchlistStatsSectionDto calculateWatchlistStatsSeries() {
        WatchlistStatsSectionDto stats = new WatchlistStatsSectionDto();

        long total = seriesWatchlistRepository.count();
        long usuariosConLista = seriesWatchlistRepository.countDistinctUsers();

        stats.setTotalGuardadas(total);
        stats.setUsuariosConLista(usuariosConLista);
        stats.setPromedioPorUsuario(usuariosConLista > 0 ?
                Math.round((double) total / usuariosConLista * 10) / 10.0 : 0);

        stats.setTopContent(seriesWatchlistRepository.findTopSeries(PageRequest.of(0, 10))
                .stream().map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("titulo", row[0]);
                    m.put("total", row[1]);
                    return m;
                }).toList());

        stats.setGeneros(calcularGenerosConPorcentaje(seriesWatchlistRepository.findAllSeriesGenres()));

        long conMotivo = seriesWatchlistRepository.countByMotivoIsNotNull();
        stats.setPctConMotivo(total > 0 ? Math.round(conMotivo * 1000.0 / total) / 10.0 : 0);
        stats.setMotivos(calcularPorcentajes(seriesWatchlistRepository.findDistribucionMotivos()));

        return stats;
    }

    // Compartido entre Películas y Series — parsea el JSON de géneros
    // guardado como texto plano (["Drama","Fantasía"]) y arma el conteo
    // con porcentaje sobre el total de entradas de género (no de items,
    // porque cada item puede tener varios géneros).
    private List<Map<String, Object>> calcularGenerosConPorcentaje(List<String> genresRaw) {
        Map<String, Long> genreCount = new LinkedHashMap<>();
        long totalGenreEntries = 0;

        for (String json : genresRaw) {
            try {
                String cleaned = json.trim().replaceAll("^\\[|\\]$", "");
                if (cleaned.isEmpty()) continue;
                String[] parts = cleaned.split(",");
                for (String part : parts) {
                    String genre = part.trim().replaceAll("^\"|\"$", "");
                    if (!genre.isEmpty()) {
                        genreCount.merge(genre, 1L, Long::sum);
                        totalGenreEntries++;
                    }
                }
            } catch (Exception ignored) {}
        }

        final long totalEntries = totalGenreEntries;
        return genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("genero", e.getKey());
                    m.put("total", e.getValue());
                    m.put("porcentaje", totalEntries > 0 ?
                            Math.round((double) e.getValue() / totalEntries * 1000) / 10.0 : 0);
                    return m;
                }).toList();
    }

    private long calculateInactiveUsers() {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        return userRepository.countInactiveSince(threeMonthsAgo);
    }

    private double calculateGrowth(long current, long previous) {
        if (previous == 0) return current > 0 ? 100 : 0;
        return ((double) (current - previous) / previous) * 100;
    }

    // ==============================================
    // HELPER: Sanitizar listas de Maps (eliminar claves null)
    // ==============================================
    private List<Map<String, Object>> sanitizeMapList(List<Map<String, Object>> list) {
        if (list == null) return new ArrayList<>();

        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> map : list) {
            if (map == null) continue;

            Map<String, Object> cleanMap = new LinkedHashMap<>();
            boolean hasValidEntry = false;

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    cleanMap.put(entry.getKey(), entry.getValue());
                    hasValidEntry = true;
                } else {

                }
            }

            if (hasValidEntry) {
                sanitized.add(cleanMap);
            }
        }
        return sanitized;
    }
}