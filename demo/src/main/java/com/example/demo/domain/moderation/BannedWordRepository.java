package com.example.demo.domain.moderation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BannedWordRepository extends JpaRepository<BannedWord, Long> {

    // Todas las palabras de un nivel de severidad
    List<BannedWord> findBySeverityOrderByWordAsc(BannedWordSeverity severity);

    // Verificar si ya existe una palabra
    boolean existsByWordIgnoreCase(String word);

    // Todas ordenadas alfabéticamente (para el admin)
    List<BannedWord> findAllByOrderByWordAsc();

    // Todas las palabras BLOCK (para cachear en el servicio)
    @Query("SELECT b.word FROM BannedWord b WHERE b.severity = 'BLOCK'")
    List<String> findAllBlockWords();

    // Todas las palabras REVIEW (para cachear en el servicio)
    @Query("SELECT b.word FROM BannedWord b WHERE b.severity = 'REVIEW'")
    List<String> findAllReviewWords();
}
