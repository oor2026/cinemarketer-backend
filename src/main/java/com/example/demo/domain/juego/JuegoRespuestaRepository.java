package com.example.demo.domain.juego;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JuegoRespuestaRepository extends JpaRepository<JuegoRespuesta, Long> {

    List<JuegoRespuesta> findByIntentoIdOrderByOrdenAsc(Long intentoId);

    // Registra la respuesta solo si todavía no estaba respondida. Si llegan
    // dos pedidos juntos (doble toque), el segundo devuelve 0 y se descarta.
    @Modifying
    @Query("update JuegoRespuesta r set r.respondidaAt = :cuando, r.opcionElegida = :opcion, "
            + "r.correcta = :correcta, r.tiempoMs = :tiempoMs "
            + "where r.id = :id and r.respondidaAt is null")
    int marcarRespondida(@Param("id") Long id, @Param("cuando") LocalDateTime cuando,
                         @Param("opcion") Integer opcion, @Param("correcta") Boolean correcta,
                         @Param("tiempoMs") Long tiempoMs);
}
