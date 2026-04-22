package com.example.demo.domain.sweepstake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WinnerRepository extends JpaRepository<Winner, Long> {

    // Buscar ganadores de un sorteo
    List<Winner> findBySweepstakeId(Long sweepstakeId);

    // Buscar premios ganados por un usuario
    List<Winner> findByUserIdOrderByDrawDateDesc(Long userId);

    // Buscar ganadores pendientes de respuesta
    List<Winner> findByStatus(WinnerStatus status);

    // Buscar ganadores de un usuario por estado
    List<Winner> findByUserIdAndStatus(Long userId, WinnerStatus status);

    // Verificar si un usuario ya ganó un sorteo específico
    boolean existsBySweepstakeIdAndUserId(Long sweepstakeId, Long userId);

    // Contar victorias de un usuario
    long countByUserId(Long userId);

    void deleteByUser(com.example.demo.domain.user.User user);
}