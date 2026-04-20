package com.example.demo.domain.sweepstake;

import com.example.demo.domain.movie.Movie;        // 👈 IMPORTAR
import com.example.demo.domain.cinema.Cinema;      // 👈 IMPORTAR
import com.example.demo.domain.reward.Reward;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sweepstakes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sweepstake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "sweepstake_type", nullable = false, length = 20)
    private SweepstakeType sweepstakeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SweepstakeStatus status = SweepstakeStatus.DRAFT;

    // Requisitos para participar
    @Column(name = "min_points_required")
    private Integer minPointsRequired;  // Para sorteos por puntos

    @Column(name = "min_votes_required")
    private Integer minVotesRequired;   // Para sorteos por votos

    // 📌 TARGET IDs (se mantienen igual)
    @Column(name = "target_movie_id")
    private Long targetMovieId;  // Película específica (para sorteos por votos)

    @Column(name = "target_cinema_id")
    private Long targetCinemaId;  // Cine específico (para sorteos por votos)

    // 👇 NUEVAS RELACIONES VIRTUALES (solo lectura)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_movie_id", insertable = false, updatable = false)
    private Movie targetMovie;  // Relación virtual con Movie

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_cinema_id", insertable = false, updatable = false)
    private Cinema targetCinema;  // Relación virtual con Cinema

    // Fechas
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "draw_date")
    private LocalDateTime drawDate;  // Fecha en que se realizó el sorteo

    // Premio
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_id", nullable = false)
    private Reward reward;

    @Column(name = "winners_count", nullable = false)
    private Integer winnersCount = 1;  // Cantidad de ganadores

    // Relaciones
    @OneToMany(mappedBy = "sweepstake", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SweepstakeEntry> entries = new ArrayList<>();

    @OneToMany(mappedBy = "sweepstake", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Winner> winners = new ArrayList<>();

    // Metadata
    @Column(name = "created_by")
    private String createdBy;  // Admin que creó el sorteo

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Métodos útiles
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return status == SweepstakeStatus.ACTIVE &&
                now.isAfter(startDate) &&
                now.isBefore(endDate);
    }

    public boolean hasStarted() {
        return LocalDateTime.now().isAfter(startDate);
    }

    public boolean hasEnded() {
        return LocalDateTime.now().isAfter(endDate);
    }

    public boolean canUserParticipate(Integer userPoints, Integer userVotes) {
        if (status != SweepstakeStatus.ACTIVE || hasEnded() || !hasStarted()) {
            return false;
        }

        if (sweepstakeType == SweepstakeType.BY_POINTS) {
            return userPoints >= (minPointsRequired != null ? minPointsRequired : 0);
        } else { // BY_VOTE_COUNT
            return userVotes >= (minVotesRequired != null ? minVotesRequired : 0);
        }
    }

    public void addEntry(SweepstakeEntry entry) {
        entries.add(entry);
        entry.setSweepstake(this);
    }

    public void addWinner(Winner winner) {
        winners.add(winner);
        winner.setSweepstake(this);
    }
}