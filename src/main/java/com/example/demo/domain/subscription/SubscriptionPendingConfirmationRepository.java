package com.example.demo.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionPendingConfirmationRepository extends JpaRepository<SubscriptionPendingConfirmation, Long> {

    Optional<SubscriptionPendingConfirmation> findByToken(String token);

    Optional<SubscriptionPendingConfirmation> findByMpPreapprovalId(String mpPreapprovalId);

    boolean existsByMpPreapprovalId(String mpPreapprovalId);
}
