package com.example.demo.domain.banner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    // Para el admin: todos los banners
    List<Banner> findAllByOrderByCreatedAtDesc();

    // Para el dashboard: banners visibles de un módulo específico
    List<Banner> findByModuloAndVisibleTrue(BannerModulo modulo);
}