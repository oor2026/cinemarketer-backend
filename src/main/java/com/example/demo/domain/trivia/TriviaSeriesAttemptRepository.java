package com.example.demo.domain.trivia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface TriviaSeriesAttemptRepository extends JpaRepository<TriviaSeriesAttempt, Long> {
    Optional<TriviaSeriesAttempt> findByUserIdAndFecha(Long userId, LocalDate fecha);
    Optional<TriviaSeriesAttempt> findByGuestTokenAndFecha(String guestToken, LocalDate fecha);
    Optional<TriviaSeriesAttempt> findByIpInvitadoAndFecha(String ipInvitado, LocalDate fecha);
    long countByUserId(Long userId);
}
