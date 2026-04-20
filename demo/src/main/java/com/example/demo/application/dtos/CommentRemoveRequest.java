package com.example.demo.application.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentRemoveRequest {
    private String reason; // razón que escribe el admin al eliminar
}
