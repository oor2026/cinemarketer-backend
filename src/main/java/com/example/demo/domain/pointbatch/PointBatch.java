package com.example.demo.domain.pointbatch;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int points;

    @Column(name = "remaining_points", nullable = false)
    private int remainingPoints;

    @Column(name = "released_at", nullable = false)
    private LocalDateTime releasedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean expired = false;

    @PrePersist
    protected void onCreate() {
        if (remainingPoints == 0) {
            remainingPoints = points;
        }
    }
}
