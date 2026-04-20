package com.example.demo.domain.support;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SenderType senderType;  // USER o ADMIN

    @Column(name = "sender_name", length = 100)
    private String senderName;  // nombre para mostrar en el chat

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "read_by_user")
    private Boolean readByUser = false;  // el usuario leyó este mensaje (aplica a mensajes del ADMIN)

    @Column(name = "read_by_admin")
    private Boolean readByAdmin = false;  // el admin leyó este mensaje (aplica a mensajes del USER)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
