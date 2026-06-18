package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentStatsDto {
    private long totalComments;
    private double commentsPerDay;
    private double growth;
    private List<Map<String, Object>> topMovies;
    private List<Map<String, Object>> topUsers;
    private long totalReplies;
    private long gifsEnComentarios;
    private long gifsEnRespuestas;
    private double tasaGifComentarios;
    private double tasaGifRespuestas;
}