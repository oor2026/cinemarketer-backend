package com.example.demo.domain.juego;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JuegoGanadorRepository extends JpaRepository<JuegoGanador, Long> {

    List<JuegoGanador> findByCampaniaIdOrderByPuestoAsc(Long campaniaId);

    Optional<JuegoGanador> findByCampaniaIdAndParticipanteId(Long campaniaId, Long participanteId);

    // Premios notificados cuyo plazo para reclamar ya venció.
    List<JuegoGanador> findByEstadoAndReclamarHastaBefore(String estado, LocalDateTime limite);
}
