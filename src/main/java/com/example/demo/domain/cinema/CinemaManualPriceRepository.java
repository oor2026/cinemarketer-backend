package com.example.demo.domain.cinema;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CinemaManualPriceRepository extends JpaRepository<CinemaManualPrice, Long> {

    // Trae cada precio junto con su cine, sin una consulta extra por fila.
    @Query("SELECT p FROM CinemaManualPrice p JOIN FETCH p.cinema")
    List<CinemaManualPrice> findAllWithCinema();
}