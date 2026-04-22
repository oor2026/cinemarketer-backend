package com.example.demo.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    List<SubscriptionPayment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
}
