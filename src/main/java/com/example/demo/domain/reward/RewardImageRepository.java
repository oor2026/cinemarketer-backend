package com.example.demo.domain.reward;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RewardImageRepository extends JpaRepository<RewardImage, Long> {
    List<RewardImage> findByRewardIdAndRewardTypeOrderByPrimaryDesc(Long rewardId, String rewardType);
    long countByRewardIdAndRewardType(Long rewardId, String rewardType);
    Optional<RewardImage> findByRewardIdAndRewardTypeAndPrimaryTrue(Long rewardId, String rewardType);
    void deleteByRewardIdAndRewardType(Long rewardId, String rewardType);
}