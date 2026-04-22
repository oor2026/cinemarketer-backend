package com.example.demo.domain.premium;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PremiumDrawEntryRepository extends JpaRepository<PremiumDrawEntry, Long> {
    List<PremiumDrawEntry> findByRewardId(Long rewardId);
    boolean existsByRewardIdAndUserId(Long rewardId, Long userId);
    long countByRewardId(Long rewardId);
}
