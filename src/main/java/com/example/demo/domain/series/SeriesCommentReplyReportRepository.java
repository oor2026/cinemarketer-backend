package com.example.demo.domain.series;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SeriesCommentReplyReportRepository extends JpaRepository<SeriesCommentReplyReport, Long> {

    boolean existsByReplyIdAndReporterId(Long replyId, Long reporterId);

    List<SeriesCommentReplyReport> findByReplyIdOrderByCreatedAtDesc(Long replyId);

    void deleteByReplyId(Long replyId);
}