package com.example.demo.domain.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // Mensajes de un ticket ordenados por fecha
    List<SupportMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    // Marcar como leídos por el usuario todos los mensajes de ADMIN en un ticket
    @Modifying
    @Query("UPDATE SupportMessage m SET m.readByUser = true " +
           "WHERE m.ticket.id = :ticketId AND m.senderType = 'ADMIN' AND m.readByUser = false")
    void markAsReadByUser(@Param("ticketId") Long ticketId);

    // Marcar como leídos por el admin todos los mensajes de USER en un ticket
    @Modifying
    @Query("UPDATE SupportMessage m SET m.readByAdmin = true " +
           "WHERE m.ticket.id = :ticketId AND m.senderType = 'USER' AND m.readByAdmin = false")
    void markAsReadByAdmin(@Param("ticketId") Long ticketId);

    // Contar mensajes no leídos de ADMIN para un usuario específico
    @Query("SELECT COUNT(m) FROM SupportMessage m WHERE m.ticket.user.id = :userId " +
            "AND m.senderType = 'ADMIN' AND m.readByUser = false " +
            "AND m.ticket.deletedByUser = false")
    long countUnreadForUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM SupportMessage m WHERE m.ticket.id = :ticketId")
    void deleteByTicketId(@Param("ticketId") Long ticketId);
}
