package com.example.demo.application.services;

import com.example.demo.application.dtos.PointHistoryResponse;
import com.example.demo.application.dtos.PointTransactionDto;
import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.pointtransaction.PointTransaction;
import com.example.demo.domain.pointtransaction.PointTransactionRepository;
import com.example.demo.domain.pointtransaction.PointTransactionType;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PointTransactionService {

    private final PointTransactionRepository transactionRepository;

    // ==============================================
    // NUEVAS DEPENDENCIAS
    // ==============================================
    private final LevelCalculatorService levelCalculatorService;
    private final UserService userService;

    public PointTransactionService(
            PointTransactionRepository transactionRepository,
            LevelCalculatorService levelCalculatorService,
            UserService userService) {
        this.transactionRepository = transactionRepository;
        this.levelCalculatorService = levelCalculatorService;
        this.userService = userService;
    }

    /**
     * Registra una transacción de puntos ganados
     */
    @Transactional
    public void registerEarned(User user, PointAction action, int points,
                               Long referenceId, String referenceTitle) {

        // 1. Registrar transacción
        PointTransaction tx = new PointTransaction();
        tx.setUser(user);
        tx.setType(PointTransactionType.EARNED);
        tx.setAction(action);
        tx.setPoints(points);
        tx.setReferenceId(referenceId);
        tx.setReferenceTitle(referenceTitle);
        transactionRepository.save(tx);

        // 2. Los puntos ya se sumaron en el controller (ReviewController, etc.)
        //    pero necesitamos el usuario actualizado para verificar nivel
        User updatedUser = userService.getUserById(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 3. VERIFICAR SI CAMBIÓ DE NIVEL
        checkAndUpdateLevel(updatedUser);
    }

    /**
     * Registra puntos de Trivia con upsert diario: el primer acierto del día
     * crea la transacción, los siguientes aciertos del mismo día SUMAN a esa
     * misma fila en vez de insertar una nueva — así "Mis Puntos" muestra un
     * solo registro de trivia por día, no uno por cada pregunta acertada.
     */
    @Transactional
    public void registerTriviaEarned(User user, int points) {
        java.time.LocalDateTime inicioDia = java.time.LocalDate
                .now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                .atStartOfDay();
        java.time.LocalDateTime finDia = inicioDia.plusDays(1);

        java.util.Optional<PointTransaction> existente = transactionRepository
                .findFirstByUserIdAndActionAndCreatedAtBetweenOrderByCreatedAtDesc(
                        user.getId(), PointAction.TRIVIA_ANSWER, inicioDia, finDia);

        if (existente.isPresent()) {
            PointTransaction tx = existente.get();
            tx.setPoints(tx.getPoints() + points);
            transactionRepository.save(tx);
        } else {
            PointTransaction tx = new PointTransaction();
            tx.setUser(user);
            tx.setType(PointTransactionType.EARNED);
            tx.setAction(PointAction.TRIVIA_ANSWER);
            tx.setPoints(points);
            tx.setReferenceTitle("Adivina Adivinador");
            transactionRepository.save(tx);
        }

        User updatedUser = userService.getUserById(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        checkAndUpdateLevel(updatedUser);
    }

    /**
     * Registra una transacción de puntos gastados
     */
    @Transactional
    public void registerSpent(User user, PointAction action, int points,
                              Long referenceId, String referenceTitle) {

        // 1. Registrar transacción
        PointTransaction tx = new PointTransaction();
        tx.setUser(user);
        tx.setType(PointTransactionType.SPENT);
        tx.setAction(action);
        tx.setPoints(points);
        tx.setReferenceId(referenceId);
        tx.setReferenceTitle(referenceTitle);
        transactionRepository.save(tx);

        // 2. Los puntos ya se restaron en el controller (RedemptionController)
        User updatedUser = userService.getUserById(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 3. VERIFICAR SI CAMBIÓ DE NIVEL (aunque no bajamos de nivel automáticamente)
        checkAndUpdateLevel(updatedUser);
    }

    /**
     * Obtiene el historial paginado con estadísticas
     */
    public PointHistoryResponse getHistory(User user, String typeFilter, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<PointTransaction> txPage;

        if ("earned".equalsIgnoreCase(typeFilter)) {
            txPage = transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                    user.getId(), PointTransactionType.EARNED, pageable);
        } else if ("spent".equalsIgnoreCase(typeFilter)) {
            txPage = transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                    user.getId(), PointTransactionType.SPENT, pageable);
        } else if ("redeemed".equalsIgnoreCase(typeFilter)) {
            txPage = transactionRepository.findByUserIdAndTypeAndActionOrderByCreatedAtDesc(
                    user.getId(), PointTransactionType.SPENT, PointAction.REWARD_REDEMPTION, pageable);
        } else {
            txPage = transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        }

        List<PointTransactionDto> dtos = txPage.getContent().stream()
                .map(tx -> new PointTransactionDto(
                        tx.getId(),
                        tx.getType(),
                        tx.getAction(),
                        tx.getPoints(),
                        tx.getReferenceId(),
                        tx.getReferenceTitle(),
                        tx.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return new PointHistoryResponse(
                user.getAvailablePoints(),
                transactionRepository.getTotalEarned(user.getId()),
                transactionRepository.getTotalSpent(user.getId()),
                transactionRepository.getEarnedThisMonth(user.getId()),
                // Nuevos campos
                user.getAccumulatedPoints(),
                transactionRepository.getRedeemedThisMonth(user.getId()),
                user.getTotalRedeemedPoints(),
                dtos,
                page,
                txPage.getTotalPages(),
                txPage.getTotalElements()
        );
    }

    // ==============================================
    // MÉTODO PRIVADO PARA VERIFICAR NIVEL
    // ==============================================

    /**
     * Verifica si el usuario debe cambiar de nivel y lo actualiza si es necesario
     */
    private void checkAndUpdateLevel(User user) {
        // Solo verificar si el usuario no está suspendido
        if (user.isSuspended() || !user.isActive()) {
            return;
        }

        UserLevel oldLevel = user.getLevel();

        // Usar el método de la entidad para actualizar según puntos
        boolean levelChanged = user.updateLevelBasedOnPoints();

        if (levelChanged) {
            // Persistir el cambio de nivel
            userService.updateUserLevel(user.getId(), user.getLevel());

            // Aquí se podría enviar notificación al usuario
            // notifyLevelUp(user, oldLevel, user.getLevel());
        }
    }
}