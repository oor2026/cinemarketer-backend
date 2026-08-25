package com.example.demo.domain.espiritu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EspirituSnapshotRepository extends JpaRepository<EspirituSnapshot, Long> {
    Optional<EspirituSnapshot> findTopByUserIdAndTipoOrderByFechaSnapshotDesc(Long userId, String tipo);

    // "Total" = todas las fotos juntas (Películas + Series mezcladas,
    // como confirmamos) — cuenta EVENTOS de cambio dentro del período,
    // no "estado actual de cada usuario" (mismo criterio que gusto_historial).
    @Query("SELECT e.generoPrincipal as genero, e.totemNombre as totem, COUNT(e) as total " +
            "FROM EspirituSnapshot e " +
            "WHERE e.fechaSnapshot BETWEEN :start AND :end " +
            "GROUP BY e.generoPrincipal, e.totemNombre ORDER BY total DESC")
    List<Map<String, Object>> findDistribucionEspiritu(@Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    @Query("SELECT e.generoPrincipal as genero, e.totemNombre as totem, COUNT(e) as total " +
            "FROM EspirituSnapshot e " +
            "WHERE e.tipo = :tipo AND e.fechaSnapshot BETWEEN :start AND :end " +
            "GROUP BY e.generoPrincipal, e.totemNombre ORDER BY total DESC")
    List<Map<String, Object>> findDistribucionEspirituPorTipo(@Param("tipo") String tipo,
                                                              @Param("start") LocalDateTime start,
                                                              @Param("end") LocalDateTime end);
}