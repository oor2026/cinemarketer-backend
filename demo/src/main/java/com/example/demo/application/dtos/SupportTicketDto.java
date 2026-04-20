package com.example.demo.application.dtos;

import com.example.demo.domain.support.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketDto {
    private Long id;
    private String subject;
    private TicketStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private String closedBy;
    private Long userId;
    private String userName;
    private List<SupportMessageDto> messages;
    private long unreadCount;
}
