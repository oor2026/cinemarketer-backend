package com.example.demo.domain.notification;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_notification_campaigns")
@Data
@NoArgsConstructor
public class AdminNotificationCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    // SEGMENTS o USERS
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    // Solo si targetType = SEGMENTS — combinación de FREE / PREMIUM / CREATOR
    @Column(name = "target_segments", columnDefinition = "TEXT[]")
    private String[] targetSegments;

    // Solo si targetType = USERS — ids de usuario como texto
    @Column(name = "target_user_ids", columnDefinition = "TEXT[]")
    private String[] targetUserIds;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt; // null = inmediato

    @Column(nullable = false, length = 15)
    private String status = "PENDING"; // PENDING | SENT

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "recipients_count")
    private Integer recipientsCount;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}