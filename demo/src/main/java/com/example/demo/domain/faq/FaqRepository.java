package com.example.demo.domain.faq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FaqRepository extends JpaRepository<FaqItem, Long> {

    // Solo las activas ordenadas por displayOrder (para usuarios)
    List<FaqItem> findByActiveTrueOrderByDisplayOrderAsc();

    // Todas ordenadas por displayOrder (para admin)
    List<FaqItem> findAllByOrderByDisplayOrderAsc();
}
