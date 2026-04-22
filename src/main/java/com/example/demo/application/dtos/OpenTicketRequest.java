package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class OpenTicketRequest {
    private String subject;
    private String message;
}
