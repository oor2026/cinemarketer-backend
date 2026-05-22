package com.example.demo.domain.point;

public enum PointAction {
    VOTE_MOVIE,              // Votar una película        → 5 pts
    VOTE_CINEMA,             // Votar un cine             → 15 pts
    COMMENT_MOVIE,           // Comentar película         → 10 pts
    REWARD_REDEMPTION,       // Canje de premio           → variable (gasto)
    RECEIVE_MERECE_PUNTO,    // Recibir ¡Merecés un punto! en comentario → 1 pt
    REVERT_MERECE_PUNTO      // Retiro de ¡Merecés un punto! → -1 pt
}