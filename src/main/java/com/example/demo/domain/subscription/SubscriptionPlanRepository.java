package com.example.demo.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findFirstByActiveTrue();
    Optional<SubscriptionPlan> findByNameAndActiveTrue(String name);
    Optional<SubscriptionPlan> findByMpPreapprovalPlanId(String mpPreapprovalPlanId);
}
