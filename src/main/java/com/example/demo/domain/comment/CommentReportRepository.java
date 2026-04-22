package com.example.demo.domain.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {

    // Verificar si un usuario ya reportó un comentario
    boolean existsByCommentIdAndReporterId(Long commentId, Long reporterId);

    // Todos los reportes de un comentario
    List<CommentReport> findByCommentIdOrderByCreatedAtDesc(Long commentId);

    // Contar reportes de un comentario
    long countByCommentId(Long commentId);

    // Comentarios con al menos N reportes (para admin)
    @Query("SELECT DISTINCT cr.comment FROM CommentReport cr " +
           "GROUP BY cr.comment " +
           "HAVING COUNT(cr) >= :minReports " +
           "ORDER BY COUNT(cr) DESC")
    List<Comment> findCommentsWithMinReports(@Param("minReports") long minReports);

    // Eliminar todos los reportes de un comentario
    void deleteByCommentId(Long commentId);
}
