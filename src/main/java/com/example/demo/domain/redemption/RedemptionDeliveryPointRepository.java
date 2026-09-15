package com.example.demo.domain.redemption;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RedemptionDeliveryPointRepository extends JpaRepository<RedemptionDeliveryPoint, Long> {
    List<RedemptionDeliveryPoint> findByRedemptionIdOrderByDisplayOrderAsc(Long redemptionId);
    void deleteByRedemptionId(Long redemptionId);
}
