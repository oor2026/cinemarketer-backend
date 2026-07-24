package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class OpcionVotacionRequest {
    private String texto;
    private Long movieId;
}