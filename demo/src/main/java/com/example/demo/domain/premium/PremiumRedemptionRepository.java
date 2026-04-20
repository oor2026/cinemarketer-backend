package com.example.demo.domain.premium;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PremiumRedemptionRepository extends JpaRepository<PremiumRedemption, Long> {
    List<PremiumRedemption> findByUserIdOrderByRedeemedAtDesc(Long userId);
    boolean existsByRewardIdAndUserId(Long rewardId, Long userId);
}
