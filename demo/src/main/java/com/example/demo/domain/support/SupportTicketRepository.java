package com.example.demo.domain.support;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    // Tickets de un usuario ordenados por fecha desc (excluyendo los que eliminó)
    List<SupportTicket> findByUserIdAndDeletedByUserFalseOrderByCreatedAtDesc(Long userId);

    // Tickets por estado
    List<SupportTicket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    // Todos los tickets para el admin (excluyendo los que eliminó el admin)
    List<SupportTicket> findByDeletedByAdminFalseOrderByCreatedAtDesc();

    // Tickets abiertos de un usuario
    List<SupportTicket> findByUserIdAndStatus(Long userId, TicketStatus status);

    // Contar tickets abiertos de un usuario
    long countByUserIdAndStatus(Long userId, TicketStatus status);

    // Contar mensajes no leídos por el admin (mensajes de USER no leídos)
    @Query("SELECT COUNT(m) FROM SupportMessage m WHERE m.ticket.status = 'OPEN' " +
            "AND m.senderType = 'USER' AND m.readByAdmin = false")
    long countUnreadByAdmin();

    // Contar mensajes no leídos por el usuario (mensajes de ADMIN no leídos)
    @Query("SELECT COUNT(m) FROM SupportMessage m WHERE m.ticket.user.id = :userId " +
            "AND m.senderType = 'ADMIN' AND m.readByUser = false")
    long countUnreadByUser(@Param("userId") Long userId);

    // Contar tickets por estado
    long countByStatus(TicketStatus status);

    // Tiempo promedio de respuesta (en horas)
    @Query("SELECT COALESCE(AVG(timestampdiff(hour, st.createdAt, (SELECT MIN(sm.createdAt) FROM SupportMessage sm WHERE sm.ticket.id = st.id AND sm.senderType = 'ADMIN'))), 0) FROM SupportTicket st WHERE st.status = 'CLOSED'")
    double calculateAvgResponseTime();

    // Usuarios con más tickets
    @Query("SELECT t.user.id as userId, u.name as userName, COUNT(t) as ticketCount " +
            "FROM SupportTicket t JOIN User u ON t.user.id = u.id " +
            "GROUP BY t.user.id, u.name ORDER BY ticketCount DESC")
    List<Map<String, Object>> findTopUsersByTickets(Pageable pageable);

    @Query("""
    SELECT t FROM SupportTicket t
    WHERE t.user.id = :userId
    AND t.deletedByUser = false
    ORDER BY (
        SELECT COUNT(m) FROM SupportMessage m
        WHERE m.ticket.id = t.id
        AND m.senderType = 'ADMIN'
        AND m.readByUser = false
    ) DESC,
    t.createdAt DESC
    """)
    org.springframework.data.domain.Page<SupportTicket> findByUserPrioritizingUnread(
            @Param("userId") Long userId, Pageable pageable);
    org.springframework.data.domain.Page<SupportTicket> findByUserIdAndDeletedByUserFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<SupportTicket> findByUserId(Long userId);

    org.springframework.data.domain.Page<SupportTicket> findByDeletedByAdminFalseOrderByCreatedAtDesc(Pageable pageable);

    // Filtro: solo abiertos
    org.springframework.data.domain.Page<SupportTicket> findByDeletedByAdminFalseAndStatusOrderByCreatedAtDesc(
            TicketStatus status, Pageable pageable);

// Filtro: solo cerrados
// Reutiliza el mismo método con TicketStatus.CLOSED

    // Filtro: sin leer (tienen al menos un mensaje de USER no leído por el admin)
    @Query("""
    SELECT t FROM SupportTicket t
    WHERE t.deletedByAdmin = false
    AND EXISTS (
        SELECT m FROM SupportMessage m
        WHERE m.ticket.id = t.id
        AND m.senderType = 'USER'
        AND m.readByAdmin = false
    )
    ORDER BY t.createdAt DESC
    """)
    org.springframework.data.domain.Page<SupportTicket> findByDeletedByAdminFalseAndUnread(Pageable pageable);

    @Query("""
    SELECT t FROM SupportTicket t
    WHERE t.deletedByAdmin = false
    AND t.status = 'OPEN'
    AND NOT EXISTS (
        SELECT m FROM SupportMessage m
        WHERE m.ticket.id = t.id
        AND m.senderType = 'USER'
        AND m.readByAdmin = false
    )
    ORDER BY t.createdAt DESC
    """)
    org.springframework.data.domain.Page<SupportTicket> findByDeletedByAdminFalseAndOpenAndRead(Pageable pageable);
}