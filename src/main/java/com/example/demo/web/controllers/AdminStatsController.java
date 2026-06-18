package com.example.demo.web.controllers;

import com.example.demo.application.dtos.*;
import com.example.demo.domain.comment.CommentRepository;
import com.example.demo.domain.pointtransaction.PointTransactionRepository;
import com.example.demo.domain.recommendation.MovieRecommendationRepository;
import com.example.demo.domain.redemption.RedemptionRepository;
import com.example.demo.domain.redemption.RedemptionStatus;
import com.example.demo.domain.review.ReviewRepository;
import com.example.demo.domain.review.VoteType;
import com.example.demo.domain.reward.RewardRepository;
import com.example.demo.domain.support.SupportTicketRepository;
import com.example.demo.domain.support.TicketStatus;
import com.example.demo.domain.user.UserRepository;
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
    private final CommentRepository commentRepository;
    private final RedemptionRepository redemptionRepository;
    private final RewardRepository rewardRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final PremiumRewardRepository premiumRewardRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserReportRepository userReportRepository;
    private final MovieRecommendationRepository recommendationRepository;

    public AdminStatsController(
            UserRepository userRepository,
            ReviewRepository reviewRepository,
            CommentRepository commentRepository,
            RedemptionRepository redemptionRepository,
            RewardRepository rewardRepository,
            PointTransactionRepository pointTransactionRepository,
            SupportTicketRepository supportTicketRepository,
            PremiumRewardRepository premiumRewardRepository,
            UserSubscriptionRepository subscriptionRepository, UserBlockRepository userBlockRepository, UserReportRepository userReportRepository, MovieRecommendationRepository recommendationRepository) {
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
        this.redemptionRepository = redemptionRepository;
        this.rewardRepository = rewardRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.premiumRewardRepository = premiumRewardRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userBlockRepository = userBlockRepository;
        this.userReportRepository = userReportRepository;
        this.recommendationRepository = recommendationRepository;
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
        response.setVotes(calculateVoteStats(start, end));
        response.setComments(calculateCommentStats(start, end));
        response.setRedemptions(calculateRedemptionStats(start, end, prevStart, prevEnd));
        response.setPoints(calculatePointStats(start, end));
        response.setSupport(calculateSupportStats());
        response.setGrowth(calculateGrowthStats(start, end, prevStart, prevEnd));
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
        return ResponseEntity.ok(response);
    }

    private StatsSummaryDto calculateSummary(LocalDateTime start, LocalDateTime end,
                                             LocalDateTime prevStart, LocalDateTime prevEnd) {
        StatsSummaryDto summary = new StatsSummaryDto();

        // Usuarios totales
        long totalUsers = userRepository.count();

        // Ratio de aprobación
        long totalLikes = reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end);
        long totalDislikes = reviewRepository.countByVoteTypeInPeriod(VoteType.DISLIKE, start, end);
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

        long likesThisPeriod = reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end);
        long likesPrevPeriod = reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, prevStart, prevEnd);
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

    private VoteStatsDto calculateVoteStats(LocalDateTime start, LocalDateTime end) {
        VoteStatsDto stats = new VoteStatsDto();

        long likes = reviewRepository.countByVoteTypeInPeriod(VoteType.LIKE, start, end);
        long dislikes = reviewRepository.countByVoteTypeInPeriod(VoteType.DISLIKE, start, end);
        long total = likes + dislikes;

        stats.setTotalLikes(likes);
        stats.setTotalDislikes(dislikes);
        stats.setTotalVotes(total);
        stats.setApprovalRate(total > 0 ? (double) likes / total * 100 : 0);

        // Top películas más votadas - SANITIZADO
        stats.setTopMovies(sanitizeMapList(
                reviewRepository.findTopMoviesByVotes(start, end, PageRequest.of(0, 5))
        ));

        // Top usuarios que más votan - SANITIZADO
        stats.setTopUsers(sanitizeMapList(
                reviewRepository.findTopUsersByVotes(start, end, PageRequest.of(0, 5))
        ));

        // Tendencia diaria - CORREGIDO con validación null
        Map<String, Long> dailyTrend = new LinkedHashMap<>();
        List<Object[]> dailyVotes = reviewRepository.getDailyVoteCount(start, end);
        for (Object[] row : dailyVotes) {
            if (row[0] != null) {
                dailyTrend.put(row[0].toString(), ((Number) row[1]).longValue());
            } else {

            }
        }
        stats.setDailyTrend(dailyTrend);

        return stats;
    }

    private CommentStatsDto calculateCommentStats(LocalDateTime start, LocalDateTime end) {
        CommentStatsDto stats = new CommentStatsDto();

        long totalComments = commentRepository.countByCreatedAtBetween(start, end);
        stats.setTotalComments(totalComments);

        long days = java.time.Duration.between(start, end).toDays();
        stats.setCommentsPerDay(days > 0 ? (double) totalComments / days : totalComments);

        // Top películas más comentadas - SANITIZADO
        stats.setTopMovies(sanitizeMapList(
                commentRepository.findTopMoviesByComments(start, end, PageRequest.of(0, 5))
        ));

        // Top usuarios que más comentan - SANITIZADO
        stats.setTopUsers(sanitizeMapList(
                commentRepository.findTopUsersByComments(start, end, PageRequest.of(0, 5))
        ));

        return stats;
    }

    private RedemptionStatsDto calculateRedemptionStats(LocalDateTime start, LocalDateTime end,
                                                        LocalDateTime prevStart, LocalDateTime prevEnd) {
        RedemptionStatsDto stats = new RedemptionStatsDto();

        stats.setTotalRewards(rewardRepository.count());
        stats.setActiveRewards(rewardRepository.countByActiveTrue());
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
        RecommendationStatsDto stats = new RecommendationStatsDto();

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

        // Top 5 películas
        List<Object[]> topPeliculas = recommendationRepository
                .findTopMoviesByRecommendations(PageRequest.of(0, 5));
        stats.setTopPeliculas(topPeliculas.stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("titulo", row[0]);
            m.put("total", row[1]);
            return m;
        }).toList());

        // Top 5 contextos
        List<Object[]> topContextos = recommendationRepository
                .findTopContextTypes(PageRequest.of(0, 5));
        stats.setTopContextos(topContextos.stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("contexto", row[0]);
            m.put("total", row[1]);
            return m;
        }).toList());

        return stats;
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