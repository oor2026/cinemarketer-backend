package com.example.demo.domain.sweepstake;

import com.example.demo.domain.user.User;
import com.example.demo.domain.redemption.Redemption;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "winners")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Winner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sweepstake_id", nullable = false)
    private Sweepstake sweepstake;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "draw_date", nullable = false)
    private LocalDateTime drawDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WinnerStatus status = WinnerStatus.PENDING;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redemption_id")
    private Redemption redemption;  // Canje generado si acepta el premio

    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "response_date")
    private LocalDateTime responseDate;  // Fecha en que respondió (acepta/rechaza)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void setSweepstake(Sweepstake sweepstake) {
        this.sweepstake = sweepstake;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (drawDate == null) {
            drawDate = LocalDateTime.now();
        }
    }
}