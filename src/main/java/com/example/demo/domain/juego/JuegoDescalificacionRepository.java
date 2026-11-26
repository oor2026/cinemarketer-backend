package com.example.demo.domain.juego;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JuegoDescalificacionRepository extends JpaRepository<JuegoDescalificacion, Long> {

    List<JuegoDescalificacion> findByCampaniaId(Long campaniaId);

    Optional<JuegoDescalificacion> findByCampaniaIdAndParticipanteId(Long campaniaId, Long participanteId);
}
