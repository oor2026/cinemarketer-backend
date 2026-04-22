package com.example.demo.application.dtos;

import com.example.demo.domain.pointconfig.PointAction;
import com.example.demo.domain.pointtransaction.PointTransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionDto {

    private Long id;
    private PointTransactionType type;       // EARNED / SPENT
    private PointAction action;              // VOTE_MOVIE, COMMENT_MOVIE, etc.
    private Integer points;
    private Long referenceId;
    private String referenceTitle;
    private LocalDateTime createdAt;
}
