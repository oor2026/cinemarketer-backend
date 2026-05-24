package com.example.demo.domain.pointtransaction;

import com.example.demo.domain.point.PointAction;
import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private PointTransactionType type;  // EARNED o SPENT

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private PointAction action;  // VOTE_MOVIE, VOTE_CINEMA, COMMENT_MOVIE, REWARD_REDEMPTION

    @Column(name = "points", nullable = false)
    private Integer points;  // Siempre positivo

    @Column(name = "reference_id")
    private Long referenceId;  // ID de la película, cine o premio

    @Column(name = "reference_title", length = 255)
    private String referenceTitle;  // Nombre para mostrar en el historial

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
