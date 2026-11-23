package com.example.demo.domain.juego;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JuegoCampaniaRepository extends JpaRepository<JuegoCampania, Long> {

    Optional<JuegoCampania> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    List<JuegoCampania> findAllByOrderByInicioDesc();

    // Para la tarea programada: campañas que pueden cambiar de estado solas.
    List<JuegoCampania> findByEstadoIn(Collection<String> estados);
}
