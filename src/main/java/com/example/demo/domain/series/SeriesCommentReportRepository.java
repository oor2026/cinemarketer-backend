package com.example.demo.domain.series;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeriesCommentReportRepository extends JpaRepository<SeriesCommentReport, Long> {

    boolean existsByCommentIdAndReporterId(Long commentId, Long reporterId);

    List<SeriesCommentReport> findByCommentIdOrderByCreatedAtDesc(Long commentId);

    long countByCommentId(Long commentId);

    @Query("SELECT DISTINCT cr.comment FROM SeriesCommentReport cr " +
            "GROUP BY cr.comment " +
            "HAVING COUNT(cr) >= :minReports " +
            "ORDER BY COUNT(cr) DESC")
    List<SeriesComment> findCommentsWithMinReports(@Param("minReports") long minReports);

    void deleteByCommentId(Long commentId);
}