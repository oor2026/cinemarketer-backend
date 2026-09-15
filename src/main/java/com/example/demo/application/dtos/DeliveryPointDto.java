package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPointDto {
    private Long id;
    private String locationReference;
    private String scheduleInfo;
    private Integer displayOrder;
}