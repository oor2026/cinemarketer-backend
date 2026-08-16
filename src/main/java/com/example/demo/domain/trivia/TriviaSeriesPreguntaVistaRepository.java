package com.example.demo.domain.trivia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TriviaSeriesPreguntaVistaRepository extends JpaRepository<TriviaSeriesPreguntaVista, Long> {

    @Query("SELECT v.entidadId FROM TriviaSeriesPreguntaVista v WHERE v.user.id = :userId " +
            "AND v.tipo = :tipo AND v.temporadaNumero IS NULL " +
            "AND v.respondidasTotalAlAcertar + 300 > :contadorActual")
    List<Long> findEntidadIdsExcluidasSinTemporada(@Param("userId") Long userId,
                                                   @Param("tipo") TriviaTipoPreguntaSeries tipo,
                                                   @Param("contadorActual") int contadorActual);

    // Para QUIEN_ES_TEMPORADA/TEMPORADA_STILL: excluye la combinación puntual serie+temporada
    @Query("SELECT v FROM TriviaSeriesPreguntaVista v WHERE v.user.id = :userId " +
            "AND v.tipo = :tipo AND v.entidadId = :entidadId AND v.temporadaNumero = :temporadaNumero " +
            "AND v.respondidasTotalAlAcertar + 300 > :contadorActual")
    java.util.Optional<TriviaSeriesPreguntaVista> findExcluidaPorTemporada(
            @Param("userId") Long userId, @Param("tipo") TriviaTipoPreguntaSeries tipo,
            @Param("entidadId") Long entidadId, @Param("temporadaNumero") Integer temporadaNumero,
            @Param("contadorActual") int contadorActual);

    // Batch — todos los entidadId excluidos para una temporada puntual (se usa
    // para filtrar el pool de candidatos de QUIEN_ES_TEMPORADA de esa temporada)
    @Query("SELECT v.entidadId FROM TriviaSeriesPreguntaVista v WHERE v.user.id = :userId " +
            "AND v.tipo = :tipo AND v.temporadaNumero = :temporadaNumero " +
            "AND v.respondidasTotalAlAcertar + 300 > :contadorActual")
    List<Long> findEntidadIdsExcluidasPorTemporada(@Param("userId") Long userId,
                                                   @Param("tipo") TriviaTipoPreguntaSeries tipo,
                                                   @Param("temporadaNumero") Integer temporadaNumero,
                                                   @Param("contadorActual") int contadorActual);
}
