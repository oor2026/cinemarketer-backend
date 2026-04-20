package com.example.demo.domain.premium;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "premium_draw_entries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"reward_id", "user_id"}))
@NoArgsConstructor
@AllArgsConstructor
public class PremiumDrawEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_id", nullable = false)
    private PremiumReward reward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private LocalDateTime enteredAt;

    @PrePersist
    protected void onCreate() {
        enteredAt = LocalDateTime.now();
    }
}
