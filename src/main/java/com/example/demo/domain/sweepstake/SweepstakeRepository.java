package com.example.demo.domain.sweepstake;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SweepstakeRepository extends JpaRepository<Sweepstake, Long> {

    // Buscar sorteos activos
    @Query("SELECT s FROM Sweepstake s WHERE s.status = 'ACTIVE' AND s.startDate <= :now AND s.endDate >= :now")
    List<Sweepstake> findActiveSweepstakes(@Param("now") LocalDateTime now);

    // Buscar próximos sorteos
    List<Sweepstake> findByStartDateAfterAndStatusOrderByStartDateAsc(LocalDateTime date, SweepstakeStatus status);

    // Buscar sorteos finalizados que no han tenido sorteo
    List<Sweepstake> findByEndDateBeforeAndStatus(LocalDateTime date, SweepstakeStatus status);

    // Buscar por tipo
    List<Sweepstake> findBySweepstakeType(SweepstakeType type);

    // Buscar sorteos de una película específica
    List<Sweepstake> findByTargetMovieIdAndSweepstakeType(Long movieId, SweepstakeType type);

    // Buscar sorteos con premio específico
    List<Sweepstake> findByRewardId(Long rewardId);

    // Contar participantes de un sorteo
    @Query("SELECT COUNT(e) FROM SweepstakeEntry e WHERE e.sweepstake.id = :sweepstakeId")
    long countEntriesBySweepstakeId(@Param("sweepstakeId") Long sweepstakeId);

    // Verificar si un usuario ya participó
    @Query("SELECT COUNT(e) > 0 FROM SweepstakeEntry e WHERE e.sweepstake.id = :sweepstakeId AND e.user.id = :userId")
    boolean hasUserParticipated(@Param("sweepstakeId") Long sweepstakeId, @Param("userId") Long userId);

    // Buscar sorteos donde un usuario puede participar (según sus puntos/votos)
    @Query("SELECT s FROM Sweepstake s WHERE s.status = 'ACTIVE' AND s.startDate <= :now AND s.endDate >= :now AND " +
            "((s.sweepstakeType = 'BY_POINTS' AND s.minPointsRequired <= :userPoints) OR " +
            "(s.sweepstakeType = 'BY_VOTE_COUNT' AND s.minVotesRequired <= :userVotes))")
    List<Sweepstake> findEligibleSweepstakes(@Param("now") LocalDateTime now,
                                             @Param("userPoints") int userPoints,
                                             @Param("userVotes") int userVotes);
}