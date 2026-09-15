package com.example.demo.domain.premium;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PremiumRedemptionDeliveryPointRepository extends JpaRepository<PremiumRedemptionDeliveryPoint, Long> {
    List<PremiumRedemptionDeliveryPoint> findByRedemptionIdOrderByDisplayOrderAsc(Long redemptionId);
    void deleteByRedemptionId(Long redemptionId);
}
