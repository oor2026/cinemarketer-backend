package com.example.demo.domain.cinema;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    // Buscar cines por ciudad
    List<Cinema> findByCityIgnoreCase(String city);

    // Buscar cines por provincia
    List<Cinema> findByProvinceIgnoreCase(String province);

    // Buscar cines activos
    List<Cinema> findByActiveTrue();

    // Buscar por nombre (contenga el texto)
    List<Cinema> findByNameContainingIgnoreCase(String name);

    // Buscar por ciudad y provincia
    List<Cinema> findByCityIgnoreCaseAndProvinceIgnoreCase(String city, String province);

    // Buscar cines con nombre y ciudad (para búsquedas combinadas)
    @Query("SELECT c FROM Cinema c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.city) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.province) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Cinema> searchByKeyword(@Param("keyword") String keyword);

    // Verificar si existe un cine con el mismo nombre en la misma dirección
    boolean existsByNameAndAddressIgnoreCase(String name, String address);
}