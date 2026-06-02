package com.example.demo.domain.premium;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PremiumRewardRepository extends JpaRepository<PremiumReward, Long> {
    List<PremiumReward> findByActiveTrue();
    List<PremiumReward> findByActiveTrueAndType(PremiumRewardType type);
    List<PremiumReward> findByActiveTrueAndDeletedFalse();
    List<PremiumReward> findByActiveTrueAndDeletedFalseAndType(PremiumRewardType type);
    List<PremiumReward> findByDeletedFalse();
    long countByActiveTrue();
    long countByType(PremiumRewardType type);
    long countByTypeAndDrawExecutedTrue(PremiumRewardType type);
    long countByTypeAndDrawExecutedFalse(PremiumRewardType type);
    long countByTypeAndActiveTrue(PremiumRewardType type);
}
