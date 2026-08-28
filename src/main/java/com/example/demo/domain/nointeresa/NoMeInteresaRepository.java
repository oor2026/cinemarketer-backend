package com.example.demo.domain.nointeresa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NoMeInteresaRepository extends JpaRepository<NoMeInteresa, Long> {

    boolean existsByUserIdAndMovieId(Long userId, Long movieId);

    // Para filtrar el feed/carruseles: todos los movieId que el usuario
    // marcó, para excluirlos con NOT IN en las queries que arman esas
    // listas — el próximo paso, cuando me pases el controller del feed.
    @Query("SELECT n.movieId FROM NoMeInteresa n WHERE n.user.id = :userId")
    List<Long> findMovieIdsByUserId(@Param("userId") Long userId);
}