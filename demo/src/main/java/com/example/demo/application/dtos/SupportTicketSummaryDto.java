package com.example.demo.application.dtos;

import com.example.demo.domain.support.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketSummaryDto {
    private Long id;
    private String subject;
    private TicketStatus status;
    private LocalDateTime createdAt;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
    private String userName;
}
