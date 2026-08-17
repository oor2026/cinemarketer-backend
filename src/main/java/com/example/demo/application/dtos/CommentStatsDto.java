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
    private CommentStatsSectionDto total;
    private CommentStatsSectionDto peliculas;
    private CommentStatsSectionDto series;
    private double pctPeliculas;
    private double pctSeries;
    // Se mantienen a nivel global — CommentReply no distingue Películas/Series
    private long totalReplies;
    private long gifsEnComentarios;
    private long gifsEnRespuestas;
    private double tasaGifComentarios;
    private double tasaGifRespuestas;
}