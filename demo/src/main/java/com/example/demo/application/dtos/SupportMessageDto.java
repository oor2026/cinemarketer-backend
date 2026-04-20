package com.example.demo.application.dtos;

import com.example.demo.domain.support.SenderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessageDto {
    private Long id;
    private SenderType senderType;
    private String senderName;
    private String content;
    private LocalDateTime createdAt;
    private Boolean readByUser;
    private Boolean readByAdmin;
}
