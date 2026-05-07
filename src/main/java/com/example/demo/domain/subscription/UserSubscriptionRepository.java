package com.example.demo.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    @Query("SELECT s FROM UserSubscription s WHERE s.user.id = :userId ORDER BY s.createdAt DESC")
    List<UserSubscription> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT s FROM UserSubscription s WHERE s.status = 'ACTIVE' ORDER BY s.nextBillingDate ASC")
    List<UserSubscription> findAllActive();

    Optional<UserSubscription> findByMpPreapprovalId(String mpPreapprovalId);

    Optional<UserSubscription> findTopByUserIdAndStatusOrderByCreatedAtDesc(Long userId, SubscriptionStatus status);

    long countByStatus(SubscriptionStatus status);

    @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.createdAt BETWEEN :start AND :end")
    long countByCreatedAtBetween(@Param("start") java.time.LocalDateTime start,
                                 @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT s.user.id) FROM UserSubscription s WHERE s.status = 'ACTIVE'")
    long countDistinctActiveUsers();
}
