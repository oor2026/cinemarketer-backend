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
}