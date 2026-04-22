package com.example.demo.domain.pointconfig;

public enum PointAction {
    VOTE_MOVIE,         // Votar una película   → 5 pts
    VOTE_CINEMA,        // Votar un cine        → 15 pts
    COMMENT_MOVIE,      // Comentar película    → 10 pts
    REWARD_REDEMPTION   // Canje de premio      → variable (gasto)
}
