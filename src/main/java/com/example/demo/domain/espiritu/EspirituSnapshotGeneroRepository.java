package com.example.demo.domain.espiritu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EspirituSnapshotGeneroRepository extends JpaRepository<EspirituSnapshotGenero, Long> {
    List<EspirituSnapshotGenero> findBySnapshot_IdOrderByPorcentajeDesc(Long snapshotId);
}