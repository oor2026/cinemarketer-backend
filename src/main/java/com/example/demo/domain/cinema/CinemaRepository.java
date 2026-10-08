package com.example.demo.domain.cinema;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    // --- Consultas generales ---

    List<Cinema> findByCityIgnoreCase(String city);

    List<Cinema> findByProvinceIgnoreCase(String province);

    List<Cinema> findByActiveTrue();

    List<Cinema> findByNameContainingIgnoreCase(String name);

    List<Cinema> findByCityIgnoreCaseAndProvinceIgnoreCase(String city, String province);

    @Query("SELECT c FROM Cinema c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.city) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.province) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Cinema> searchByKeyword(@Param("keyword") String keyword);

    boolean existsByNameAndAddressIgnoreCase(String name, String address);

    // --- El cruce real para "cerca tuyo" ---
    // Ya no filtra por matchStatus — todo lo que está en la tabla viene
    // sincronizado directo del directorio de agendadecine.com, así que
    // "active" (el flag de moderación propio de Cinemarketer) es el único
    // filtro de negocio que queda. Un cine se guarda siempre activo salvo
    // que un admin lo oculte a mano, o que el sync lo marque inactivo por
    // haber dejado de aparecer en la fuente (ver lastSyncedAt en Cinema).
    List<Cinema> findByProvinceIgnoreCaseAndActiveTrue(String province);

    List<Cinema> findByProvinceIgnoreCaseAndCityIgnoreCaseAndActiveTrue(String province, String city);

    // Mismo criterio que arriba — directorio propio, sin derivar la cadena
    // de texto como hacía el scraper viejo: Cinema.chain ya es un dato
    // sincronizado real desde agendadecine.com.
    List<Cinema> findByChainIgnoreCaseAndActiveTrue(String chain);

    // --- Import/sincronización ---
    // Clave real contra agendadecine.com: dado el complexId que trae una
    // función o una fila del directorio, encontrar la fila local
    // correspondiente — para decidir si el sync actualiza o inserta.
    Optional<Cinema> findByAgendaDeCineComplexId(String agendaDeCineComplexId);

    boolean existsByAgendaDeCineComplexId(String agendaDeCineComplexId);
}