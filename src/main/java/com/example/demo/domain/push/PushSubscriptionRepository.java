package com.example.demo.domain.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserId(Long userId);
    Optional<PushSubscription> findByUserIdAndEndpoint(Long userId, String endpoint);
    void deleteByUserIdAndEndpoint(Long userId, String endpoint);
    List<PushSubscription> findAll();
}