package com.example.demo.domain.expectation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MovieExpectationRepository extends JpaRepository<MovieExpectation, Long> {

    Optional<MovieExpectation> findByUserIdAndMovieId(Long userId, Long movieId);

    long countByMovieIdAndExpectingTrue(Long movieId);

    List<MovieExpectation> findByNotifyOnReleaseTrueAndNotifiedFalse();

    // ===== Estadísticas del admin: "Lo que se viene" =====
    // Se filtra por createdAt (primera respuesta del usuario a esa película);
    // hay una fila por usuario+película, así que cambiar de Sí a No no cuenta doble.

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByExpectingAndCreatedAtBetween(boolean expecting, LocalDateTime start, LocalDateTime end);

    long countByExpectingTrueAndNotifyOnReleaseTrueAndCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT e.user.id) FROM MovieExpectation e " +
            "WHERE e.createdAt BETWEEN :start AND :end")
    long countDistinctUsersInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Más esperadas: más "Sí"; a igual cantidad, la que tenga menos "No".
    @Query("SELECT e.movieId AS movieId, " +
            "SUM(CASE WHEN e.expecting = true THEN 1L ELSE 0L END) AS siTotal, " +
            "SUM(CASE WHEN e.expecting = false THEN 1L ELSE 0L END) AS noTotal " +
            "FROM MovieExpectation e WHERE e.createdAt BETWEEN :start AND :end " +
            "GROUP BY e.movieId " +
            "HAVING SUM(CASE WHEN e.expecting = true THEN 1L ELSE 0L END) > 0 " +
            "ORDER BY SUM(CASE WHEN e.expecting = true THEN 1L ELSE 0L END) DESC, " +
            "SUM(CASE WHEN e.expecting = false THEN 1L ELSE 0L END) ASC, e.movieId ASC")
    List<Map<String, Object>> findTopMasEsperadas(@Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end, Pageable pageable);

    // Menos esperadas: más "No"; a igual cantidad, la que tenga menos "Sí".
    @Query("SELECT e.movieId AS movieId, " +
            "SUM(CASE WHEN e.expecting = true THEN 1L ELSE 0L END) AS siTotal, " +
            "SUM(CASE WHEN e.expecting = false THEN 1L ELSE 0L END) AS noTotal " +
            "FROM MovieExpectation e WHERE e.createdAt BETWEEN :start AND :end " +
            "GROUP BY e.movieId " +
            "HAVING SUM(CASE WHEN e.expecting = false THEN 1L ELSE 0L END) > 0 " +
            "ORDER BY SUM(CASE WHEN e.expecting = false THEN 1L ELSE 0L END) DESC, " +
            "SUM(CASE WHEN e.expecting = true THEN 1L ELSE 0L END) ASC, e.movieId ASC")
    List<Map<String, Object>> findTopMenosEsperadas(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT e.user.id AS userId, e.user.name AS name, COUNT(e) AS total, " +
            "SUM(CASE WHEN e.expecting = true THEN 1L ELSE 0L END) AS siTotal, " +
            "SUM(CASE WHEN e.expecting = false THEN 1L ELSE 0L END) AS noTotal " +
            "FROM MovieExpectation e WHERE e.createdAt BETWEEN :start AND :end " +
            "GROUP BY e.user.id, e.user.name ORDER BY COUNT(e) DESC, e.user.id ASC")
    List<Map<String, Object>> findTopUsuarios(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end, Pageable pageable);
}