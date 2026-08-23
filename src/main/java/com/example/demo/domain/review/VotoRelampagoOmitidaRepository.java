package com.example.demo.domain.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VotoRelampagoOmitidaRepository extends JpaRepository<VotoRelampagoOmitida, Long> {

    Optional<VotoRelampagoOmitida> findByUserIdAndMovieId(Long userId, Long movieId);

    // IDs de películas que hoy siguen "bloqueadas" para la rueda de Voto
    // Relámpago: el usuario dijo que no la vio, nunca la votó después, y
    // todavía no pasaron los 20 días desde que la omitió.
    @Query("SELECT o.movieId FROM VotoRelampagoOmitida o " +
            "WHERE o.user.id = :userId " +
            "AND o.supersededByVote = false " +
            "AND o.createdAt > :cutoff")
    List<Long> findActivasMovieIds(@Param("userId") Long userId, @Param("cutoff") LocalDateTime cutoff);

    // Todos los registros de un usuario para una película (para la lógica
    // de "matar" al votar, sin importar si están vencidos o no)
    List<VotoRelampagoOmitida> findAllByUserIdAndMovieId(Long userId, Long movieId);

    // Total de "No la vi" vigentes creadas/actualizadas dentro del
    // período — mismo criterio que countByVoteTypeInPeriod en
    // ReviewRepository, pero acá "vigente" también excluye las que el
    // usuario ya votó después (supersededByVote=true).
    @Query("SELECT COUNT(o) FROM VotoRelampagoOmitida o " +
            "WHERE o.supersededByVote = false AND o.createdAt BETWEEN :start AND :end")
    long countVigentesInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT FUNCTION('DATE', o.createdAt) as date, COUNT(o) as count " +
            "FROM VotoRelampagoOmitida o " +
            "WHERE o.supersededByVote = false AND o.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', o.createdAt) ORDER BY date")
    List<Object[]> getDailyOmitidasCount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Top 10 — a propósito SIN filtro de fecha: mide el estado actual
    // ("cuántos la tienen marcada como no vista HOY"), no lo que pasó
    // en el período del filtro del admin. Ver conversación: el objetivo
    // es detectar contenido con alto "no la vi" vigente para accionar
    // marketing, sin importar cuándo se marcó.
    @Query("SELECT o.movieId as id, COALESCE(m.title, 'Película ' || o.movieId) as title, COUNT(o) as total " +
            "FROM VotoRelampagoOmitida o LEFT JOIN Movie m ON o.movieId = m.tmdbId " +
            "WHERE o.supersededByVote = false " +
            "GROUP BY o.movieId, m.title ORDER BY total DESC")
    List<java.util.Map<String, Object>> findTopOmitidasVigentes(org.springframework.data.domain.Pageable pageable);
}
