package com.example.demo.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Ultimas 30 notificaciones del usuario ordenadas por fecha
    List<Notification> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);

    // Contar no leidas
    long countByUserIdAndReadFalse(Long userId);

    // Marcar todas como leidas
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    void markAllAsRead(@Param("userId") Long userId);

    // Eliminar notificaciones viejas (mas de 30 dias) — para limpieza
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") java.time.LocalDateTime cutoff);

    // Buscar notificación existente por destinatario + actor + tipo (para upsert) — trae la más reciente
    java.util.Optional<Notification> findTopByUserIdAndActorIdAndTypeOrderByCreatedAtDesc(
            Long userId, Long actorId, NotificationType type);
}
