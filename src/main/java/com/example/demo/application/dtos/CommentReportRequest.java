package com.example.demo.application.dtos;

import com.example.demo.domain.comment.ReportReason;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentReportRequest {
    private ReportReason reason;
    private String description;
}
