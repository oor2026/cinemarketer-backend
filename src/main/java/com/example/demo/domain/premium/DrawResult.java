package com.example.demo.domain.premium;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "draw_results")
@NoArgsConstructor
@AllArgsConstructor
public class DrawResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_id", nullable = false)
    private PremiumReward reward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 1 = ganador, 2 = suplente 1, 3 = suplente 2
    @Column(nullable = false)
    private int position;

    // ACTIVO, DESCALIFICADO
    @Column(length = 20, nullable = false)
    private String status = "ACTIVO";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}