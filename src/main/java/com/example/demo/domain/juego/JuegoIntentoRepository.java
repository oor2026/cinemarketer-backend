package com.example.demo.domain.juego;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JuegoIntentoRepository extends JpaRepository<JuegoIntento, Long> {

    Optional<JuegoIntento> findByCampaniaIdAndParticipanteIdAndDia(Long campaniaId, Long participanteId, LocalDate dia);

    // Todas las partidas de la campaña: con esto se arma el ranking acumulado.
    List<JuegoIntento> findByCampaniaId(Long campaniaId);
}
