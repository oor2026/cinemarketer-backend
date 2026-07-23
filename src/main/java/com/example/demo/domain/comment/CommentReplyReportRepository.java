package com.example.demo.domain.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentReplyReportRepository extends JpaRepository<CommentReplyReport, Long> {

    boolean existsByReplyIdAndReporterId(Long replyId, Long reporterId);

    List<CommentReplyReport> findByReplyIdOrderByCreatedAtDesc(Long replyId);

    void deleteByReplyId(Long replyId);
}