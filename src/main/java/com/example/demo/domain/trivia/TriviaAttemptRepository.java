package com.example.demo.domain.trivia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface TriviaAttemptRepository extends JpaRepository<TriviaAttempt, Long> {
    Optional<TriviaAttempt> findByUserIdAndFecha(Long userId, LocalDate fecha);
    Optional<TriviaAttempt> findByGuestTokenAndFecha(String guestToken, LocalDate fecha);
}