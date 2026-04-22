package com.example.demo.domain.sweepstake;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sweepstake_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sweepstake_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SweepstakeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sweepstake_id", nullable = false)
    private Sweepstake sweepstake;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    @Column(name = "user_points_at_entry")
    private Integer userPointsAtEntry;  // Puntos del usuario al momento de participar

    @Column(name = "user_votes_at_entry")
    private Integer userVotesAtEntry;  // Votos del usuario al momento de participar

    @Column(nullable = false)
    private Boolean active = true;

    @PrePersist
    protected void onCreate() {
        entryDate = LocalDateTime.now();
    }

    public void setSweepstake(Sweepstake sweepstake) {
        this.sweepstake = sweepstake;
    }

}