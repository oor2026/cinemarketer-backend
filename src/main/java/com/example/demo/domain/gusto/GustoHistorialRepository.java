package com.example.demo.domain.gusto;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GustoHistorialRepository extends JpaRepository<GustoHistorial, Long> {
    Optional<GustoHistorial> findTopByUserIdAndTipoAndCampoOrderByFechaDetectadoDesc(
            Long userId, String tipo, String campo);

    // Para "Total": se agrupa también por tipo, no solo por contenido_id,
    // porque un movieId y un seriesId pueden coincidir en número siendo
    // contenido distinto — si no, se mezclarían mal en el ranking.
    @Query("SELECT g.tipo as tipo, g.contenidoId as id, g.contenidoTitulo as titulo, COUNT(g) as total " +
            "FROM GustoHistorial g " +
            "WHERE g.campo = :campo AND g.fechaDetectado BETWEEN :start AND :end " +
            "GROUP BY g.tipo, g.contenidoId, g.contenidoTitulo ORDER BY total DESC")
    List<Map<String, Object>> findTopGustoTotal(@Param("campo") String campo,
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                Pageable pageable);

    @Query("SELECT g.contenidoId as id, g.contenidoTitulo as titulo, COUNT(g) as total " +
            "FROM GustoHistorial g " +
            "WHERE g.tipo = :tipo AND g.campo = :campo AND g.fechaDetectado BETWEEN :start AND :end " +
            "GROUP BY g.contenidoId, g.contenidoTitulo ORDER BY total DESC")
    List<Map<String, Object>> findTopGustoPorTipo(@Param("tipo") String tipo,
                                                  @Param("campo") String campo,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end,
                                                  Pageable pageable);
}