package com.example.demo.application.dtos;

import lombok.Data;

@Data
public class MovieExpectationDto {
    private Long count;              // cuántos dijeron que SÍ la esperan
    private Boolean userExpecting;   // true=Sí, false=No, null=todavía no respondió
    private Boolean notifyOnRelease;
}