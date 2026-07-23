package com.example.demo.domain.publication;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    Optional<Hashtag> findByNombre(String nombre);

    // Para el autocompletado (próximo paso): trae los que empiezan con el
    // prefijo tipeado, ordenados por más usados primero. usageCount > 0
    // evita mostrar hashtags que quedaron en 0 tras ocultarse todas sus publicaciones.
    @Query("SELECT h FROM Hashtag h WHERE h.nombre LIKE CONCAT(:prefijo, '%') AND h.usageCount > 0 ORDER BY h.usageCount DESC")
    List<Hashtag> findTopByPrefijo(@Param("prefijo") String prefijo, Pageable pageable);
}