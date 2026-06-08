package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SuggestedUserDto {
    private Long id;
    private String name;
    private String profileImageUrl;
}
