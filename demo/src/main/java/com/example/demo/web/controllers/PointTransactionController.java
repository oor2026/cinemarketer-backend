package com.example.demo.web.controllers;

import com.example.demo.application.dtos.PointHistoryResponse;
import com.example.demo.application.services.PointTransactionService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/points")
@CrossOrigin(origins = "http://localhost:63342")
public class PointTransactionController {

    private final PointTransactionService pointTransactionService;
    private final UserRepository userRepository;

    public PointTransactionController(PointTransactionService pointTransactionService,
                                      UserRepository userRepository) {
        this.pointTransactionService = pointTransactionService;
        this.userRepository = userRepository;
    }

    /**
     * Obtener historial de puntos del usuario autenticado
     * GET /api/users/me/points/history?filter=all&page=1&size=10
     *
     * filter: all | earned | spent
     */
    @GetMapping("/history")
    public ResponseEntity<PointHistoryResponse> getPointHistory(
            @RequestParam(required = false, defaultValue = "all") String filter,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        PointHistoryResponse history = pointTransactionService.getHistory(user, filter, page, size);
        return ResponseEntity.ok(history);
    }
}
