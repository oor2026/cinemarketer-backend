package com.example.demo.domain.premium;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DrawResultRepository extends JpaRepository<DrawResult, Long> {
    List<DrawResult> findByRewardIdOrderByPosition(Long rewardId);
    Optional<DrawResult> findByRewardIdAndPosition(Long rewardId, int position);
    boolean existsByRewardId(Long rewardId);
}