package com.example.demo.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    // Total recaudado (pagos aprobados)
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SubscriptionPayment p WHERE p.status = 'approved'")
    BigDecimal sumTotalApproved();

    // Recaudado en período
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SubscriptionPayment p " +
            "WHERE p.status = 'approved' AND p.paidAt BETWEEN :start AND :end")
    BigDecimal sumApprovedInPeriod(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    // Conteo de pagos por estado en período
    long countByStatusAndPaidAtBetween(String status, LocalDateTime start, LocalDateTime end);

    // Conteo total por estado
    long countByStatus(String status);

    // Pagos del período ordenados por fecha
    @Query("SELECT p FROM SubscriptionPayment p WHERE p.paidAt BETWEEN :start AND :end " +
            "ORDER BY p.paidAt DESC")
    List<SubscriptionPayment> findInPeriod(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    // Ingreso por mes (para tendencia)
    @Query(value = """
        SELECT TO_CHAR(paid_at, 'YYYY-MM') as mes, COALESCE(SUM(amount), 0) as total
        FROM subscription_payments
        WHERE status = 'approved' AND paid_at IS NOT NULL
        GROUP BY mes ORDER BY mes DESC
        LIMIT 12
        """, nativeQuery = true)
    List<Object[]> findMonthlyRevenue();
}