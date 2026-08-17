package com.example.demo.domain.series;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "voto_relampago_omitidas_series",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "series_id"}))
@Data
@NoArgsConstructor
public class VotoRelampagoOmitidaSerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "superseded_by_vote", nullable = false)
    private boolean supersededByVote = false;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}