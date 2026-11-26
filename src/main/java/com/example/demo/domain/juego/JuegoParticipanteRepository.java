package com.example.demo.domain.juego;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JuegoParticipanteRepository extends JpaRepository<JuegoParticipante, Long> {
    Optional<JuegoParticipante> findByCampaniaIdAndUserId(Long campaniaId, Long userId);
    Optional<JuegoParticipante> findByCampaniaIdAndEmailNormalizado(Long campaniaId, String emailNormalizado);
    long countByCampaniaId(Long campaniaId);
}
