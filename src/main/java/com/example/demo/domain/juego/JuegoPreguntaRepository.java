package com.example.demo.domain.juego;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JuegoPreguntaRepository extends JpaRepository<JuegoPregunta, Long> {

    // Todo el banco: primero las asignadas por día y orden, después las libres.
    List<JuegoPregunta> findByCampaniaIdOrderByDiaAsignadoAscOrdenEnDiaAscIdAsc(Long campaniaId);

    // La tanda de un día, en orden.
    List<JuegoPregunta> findByCampaniaIdAndDiaAsignadoOrderByOrdenEnDiaAsc(Long campaniaId, LocalDate dia);

    long countByCampaniaId(Long campaniaId);
}
