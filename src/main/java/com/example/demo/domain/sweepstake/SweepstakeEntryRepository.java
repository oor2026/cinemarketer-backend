package com.example.demo.domain.sweepstake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SweepstakeEntryRepository extends JpaRepository<SweepstakeEntry, Long> {

    // Buscar participaciones de un usuario
    List<SweepstakeEntry> findByUserIdOrderByEntryDateDesc(Long userId);

    // Buscar participantes de un sorteo
    List<SweepstakeEntry> findBySweepstakeId(Long sweepstakeId);

    // Verificar si un usuario ya participó
    boolean existsBySweepstakeIdAndUserId(Long sweepstakeId, Long userId);

    // Contar participantes de un sorteo
    long countBySweepstakeId(Long sweepstakeId);

    // Eliminar participación (si el usuario quiere salir)
    void deleteBySweepstakeIdAndUserId(Long sweepstakeId, Long userId);

    void deleteByUser(com.example.demo.domain.user.User user);
}