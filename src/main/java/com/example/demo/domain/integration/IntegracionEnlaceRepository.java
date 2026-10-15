package com.example.demo.domain.integracion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface IntegracionEnlaceRepository extends JpaRepository<IntegracionEnlace, Long> {

    // Para el atajo: solo los activos de un tipo, en el orden elegido en el admin.
    List<IntegracionEnlace> findByTipoAndActivoTrueOrderByOrdenAscCadenaAsc(String tipo);

    // Para el admin: todos los de un tipo.
    List<IntegracionEnlace> findByTipoOrderByOrdenAscCadenaAsc(String tipo);

    // Suma un clic en la base misma, sin leer y volver a guardar: dos clics al mismo
    // tiempo cuentan los dos.
    @Modifying
    @Transactional
    @Query("UPDATE IntegracionEnlace e SET e.clics = e.clics + 1 WHERE e.id = :id")
    int sumarClic(@Param("id") Long id);
}
