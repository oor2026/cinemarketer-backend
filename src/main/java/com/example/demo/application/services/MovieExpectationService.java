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
        Double avg = expectationRepository.findAverageByMovieId(movieId);
        dto.setAverage(avg != null ? avg : 0.0);
        dto.setCount(expectationRepository.countByMovieId(movieId));

        if (userEmail != null) {
            User user = userRepository.findByEmail(userEmail).orElse(null);
            if (user != null) {
                expectationRepository.findByUserIdAndMovieId(user.getId(), movieId)
                        .ifPresent(e -> dto.setUserRating(e.getRating()));
            }
        }
        return dto;
    }

    public MovieExpectationDto rate(Long movieId, String userEmail, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("La calificación debe ser entre 1 y 5");
        }
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        MovieExpectation exp = expectationRepository.findByUserIdAndMovieId(user.getId(), movieId)
                .orElseGet(MovieExpectation::new);
        exp.setUser(user);
        exp.setMovieId(movieId);
        exp.setRating(rating);
        expectationRepository.save(exp);

        return getExpectation(movieId, userEmail);
    }
}