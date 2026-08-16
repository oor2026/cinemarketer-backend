package com.example.demo.domain.point;

public enum PointAction {
    VOTE_MOVIE,              // Votar una película        → 10 pts
    VOTE_CINEMA,             // Votar un cine             → 15 pts
    VOTE_SERIES,             // Votar una serie            → misma escala que VOTE_MOVIE
    COMMENT_MOVIE,           // Comentar película         → 40 pts
    COMMENT_SERIES,          // Comentar serie             → misma escala que COMMENT_MOVIE
    RECOMMEND_MOVIE,         // Recomendar película         → 25 pts
    RECOMMEND_SERIES,        // Recomendar serie            → 25 pts
    REWARD_REDEMPTION,       // Canje de premio           → variable (gasto)
    RECEIVE_MERECE_PUNTO,    // Recibir ¡Merecés un punto! en comentario → 1 pt
    REVERT_MERECE_PUNTO,    // Reversión de ¡Merecés un punto!
    ADMIN_GRANT,            // Otorgado manualmente por admin
    PUBLISH_POST,           // Publicar en Comunidad → variable según plan
    RECEIVE_BANCO_POST,     // Recibir Te banco en publicación → 1 pt
    RECEIVE_MERECE_POST,     // Recibir Merecés un punto en publicación → 1 pt
    PUBLICATION_SANCTION,
    TRIVIA_ANSWER,           // Acertar pregunta en Adivina Adivinador → 5 pts
    TRIVIA_SERIES_ANSWER     // Acertar pregunta en Trivia de Series → 5 pts

}