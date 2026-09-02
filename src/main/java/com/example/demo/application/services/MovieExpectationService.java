package com.example.demo.application.services;

import com.example.demo.application.dtos.MovieExpectationDto;
import com.example.demo.domain.expectation.MovieExpectation;
import com.example.demo.domain.expectation.MovieExpectationRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class MovieExpectationService {

    private final MovieExpectationRepository expectationRepository;
    private final UserRepository userRepository;

    public MovieExpectationService(MovieExpectationRepository expectationRepository, UserRepository userRepository) {
        this.expectationRepository = expectationRepository;
        this.userRepository = userRepository;
    }

    public MovieExpectationDto getExpectation(Long movieId, String userEmail) {
        MovieExpectationDto dto = new MovieExpectationDto();
        dto.setCount(expectationRepository.countByMovieIdAndExpectingTrue(movieId));

        if (userEmail != null) {
            User user = userRepository.findByEmail(userEmail).orElse(null);
            if (user != null) {
                expectationRepository.findByUserIdAndMovieId(user.getId(), movieId)
                        .ifPresent(e -> {
                            dto.setUserExpecting(e.isExpecting());
                            dto.setNotifyOnRelease(e.isNotifyOnRelease());
                        });
            }
        }
        return dto;
    }

    public MovieExpectationDto rate(Long movieId, String userEmail, boolean expecting) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        MovieExpectation exp = expectationRepository.findByUserIdAndMovieId(user.getId(), movieId)
                .orElseGet(MovieExpectation::new);
        exp.setUser(user);
        exp.setMovieId(movieId);
        exp.setExpecting(expecting);
        expectationRepository.save(exp);

        return getExpectation(movieId, userEmail);
    }

    public MovieExpectationDto activarAvisoEstreno(Long movieId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        MovieExpectation exp = expectationRepository.findByUserIdAndMovieId(user.getId(), movieId)
                .orElseThrow(() -> new IllegalArgumentException("Primero decí si la esperás"));

        exp.setNotifyOnRelease(true);
        expectationRepository.save(exp);

        return getExpectation(movieId, userEmail);
    }
}