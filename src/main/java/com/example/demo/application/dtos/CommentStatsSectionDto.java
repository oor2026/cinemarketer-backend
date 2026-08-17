package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentStatsSectionDto {
    private long totalComments;
    private double commentsPerDay;
    private double growth;
    private List<Map<String, Object>> topContent;
    private List<Map<String, Object>> topUsers;
}