package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class VoteRequest {
    private String voteType; // "LIKE" o "DISLIKE"
}