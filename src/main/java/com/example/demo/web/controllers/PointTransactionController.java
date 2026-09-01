package com.example.demo.web.controllers;

import com.example.demo.application.dtos.PointHistoryResponse;
import com.example.demo.application.dtos.PointsResumenDto;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.pointbatch.PointBatch;
import com.example.demo.domain.pointbatch.PointBatchRepository;
import com.example.demo.domain.pointtransaction.PointTransactionRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/points")
public class PointTransactionController {

    private final PointTransactionService pointTransactionService;
    private final UserRepository userRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PointBatchRepository pointBatchRepository;

    public PointTransactionController(PointTransactionService pointTransactionService,
                                      UserRepository userRepository,
                                      PointTransactionRepository pointTransactionRepository,
                                      PointBatchRepository pointBatchRepository) {
        this.pointTransactionService = pointTransactionService;
        this.userRepository = userRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.pointBatchRepository = pointBatchRepository;
    }

    /**
     * Obtener historial de puntos del usuario autenticado
     * GET /api/users/me/points/history?filter=all&page=1&size=10
     *
     * filter: all | earned | spent
     */
    @GetMapping("/history")
    public ResponseEntity<PointHistoryResponse> getPointHistory(
            @RequestParam(required = false, defaultValue = "all") String filter,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        PointHistoryResponse history = pointTransactionService.getHistory(user, filter, page, size);
        return ResponseEntity.ok(history);
    }

    /**
     * Resumen "todo en uno" para el buscador asistido de Club de
     * Beneficios — cruza puntos, tope mensual, nivel/insignia y cupo
     * diario en una sola llamada, en vez de que el frontend arme una
     * respuesta rica pegándole a 3-4 endpoints distintos.
     * GET /api/users/me/points/resumen
     */
    @GetMapping("/resumen")
    public ResponseEntity<PointsResumenDto> getResumen(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean premium = user.isActivePremium();
        java.time.LocalDate hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

        // Mismo criterio de reset diario que CommentController/
        // RecommendationController — acá solo se LEE el valor efectivo
        // de hoy, sin persistir ningún reset (eso sigue pasando solo
        // cuando el usuario efectivamente comenta/recomienda).
        int comentariosHoy = hoy.equals(user.getLastCommentDate()) ? user.getDailyCommentCount() : 0;
        int recomendacionesHoy = hoy.equals(user.getLastRecommendationDate()) ? user.getDailyRecommendationCount() : 0;

        PointsResumenDto dto = new PointsResumenDto();
        dto.setAvailablePoints(user.getAvailablePoints());
        dto.setAccumulatedPoints(user.getAccumulatedPoints());
        dto.setMonthlyCap(user.getEffectiveMonthlyCap());
        dto.setEarnedThisMonth(pointTransactionRepository.getEarnedThisMonth(user.getId()));
        dto.setPremium(premium);
        dto.setCreator(user.isActiveCreator());
        dto.setLevel(user.getLevel().name());
        dto.setNextLevel(user.getNextLevel() != null ? user.getNextLevel().name() : null);
        dto.setPointsToNextLevel(user.getPointsToNextLevel());
        dto.setProgressToNextLevel(user.getProgressToNextLevel());
        dto.setDailyCommentsUsed(comentariosHoy);
        dto.setDailyCommentsLimit(premium ? null : 10);
        dto.setDailyRecommendationsUsed(recomendacionesHoy);
        dto.setDailyRecommendationsLimit(premium ? null : 3);

        // Próximo lote a vencer — FIFO, el primero de la lista con
        // expiresAt no nulo (Premium tiene todos sus lotes con
        // expiresAt=null, así que naturalmente no encuentra ninguno).
        if (!premium) {
            List<PointBatch> lotes = pointBatchRepository.findActiveBatchesByUserId(user.getId());
            lotes.stream()
                    .filter(b -> b.getExpiresAt() != null)
                    .findFirst()
                    .ifPresent(lote -> {
                        dto.setNextExpirationDate(lote.getExpiresAt());
                        dto.setNextExpirationPoints(lote.getRemainingPoints());
                    });
        }

        return ResponseEntity.ok(dto);
    }
}
