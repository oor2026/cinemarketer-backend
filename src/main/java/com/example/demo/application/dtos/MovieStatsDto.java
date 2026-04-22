package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MovieStatsDto {
    private Long movieId;
    private long likes;
    private long dislikes;
    private double positivePercentage; // % de likes sobre total de votos
    private long totalVotes;
    private boolean userVoted; // Indica si el usuario actual ya votó
    private String userVoteType; // "LIKE" o "DISLIKE" si ya votó
}