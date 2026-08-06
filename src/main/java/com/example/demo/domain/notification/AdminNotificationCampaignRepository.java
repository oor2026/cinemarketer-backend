package com.example.demo.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface AdminNotificationCampaignRepository extends JpaRepository<AdminNotificationCampaign, Long> {
    List<AdminNotificationCampaign> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime now);
    List<AdminNotificationCampaign> findAllByOrderByCreatedAtDesc();
}