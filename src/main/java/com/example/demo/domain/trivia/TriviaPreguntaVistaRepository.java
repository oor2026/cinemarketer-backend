package com.example.demo.domain.trivia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TriviaPreguntaVistaRepository extends JpaRepository<TriviaPreguntaVista, Long> {

    Optional<TriviaPreguntaVista> findByUserIdAndTipoAndEntidadId(
            Long userId, TriviaTipoPregunta tipo, Long entidadId);

    // IDs excluidos hoy: los que el usuario ya acertó y todavía no pasaron
    // las 300 respuestas de diferencia desde que los acertó.
    @Query("SELECT v.entidadId FROM TriviaPreguntaVista v WHERE v.user.id = :userId " +
            "AND v.tipo = :tipo AND v.respondidasTotalAlAcertar + 300 > :contadorActual")
    List<Long> findEntidadIdsExcluidas(@Param("userId") Long userId,
                                       @Param("tipo") TriviaTipoPregunta tipo,
                                       @Param("contadorActual") int contadorActual);
}