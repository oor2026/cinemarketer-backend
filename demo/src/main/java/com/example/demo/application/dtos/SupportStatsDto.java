package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportStatsDto {
    private long openTickets;
    private long closedTickets;
    private double avgResponseTimeHours;
    private List<Map<String, Object>> topUsers;
}